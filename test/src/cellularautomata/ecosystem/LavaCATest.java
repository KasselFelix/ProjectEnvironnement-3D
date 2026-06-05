package cellularautomata.ecosystem;

import landscapegenerator.PerlinNoiseLandscapeGenerator;
import objects.Layer;
import objects.LavaLineage;
import objects.Material;
import objects.dynamic.TephraProjectile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests des mécaniques volcaniques : tephra, lave persistante, fonte de pierre.
 *
 * Stratégie : on instancie un vrai WorldOfCells (nbloups/moutons/humains=0) et
 * on appelle directement `world.lavaCA.step()` pour faire avancer le CA sans
 * passer par tout le pipeline d'itération. LavaCA.step() ne touche pas OpenGL.
 *
 * Plusieurs champs de LavaCA sont static (bErupt, sourceX, sourceY,
 * currentPower) — `@AfterEach` reset bErupt pour ne pas contaminer le test
 * suivant.
 */
class LavaCATest {

    private static final int DX_VIEW = 51;
    private static final int DY_VIEW = 51;
    private static final int DX = DX_VIEW - 1;
    private static final int DY = DY_VIEW - 1;

    private WorldOfCells buildWorld() {
        WorldOfCells world = new WorldOfCells();
        world.nbloups = 0;
        world.nbmoutons = 0;
        world.nbhumains = 0;
        double[][] landscape = PerlinNoiseLandscapeGenerator
                .generatePerlinNoiseLandscape(DX_VIEW, DY_VIEW, 0.7, 0.4, 4);
        world.init(DX, DY, landscape);
        return world;
    }

    @AfterEach
    void resetEruption() {
        LavaCA.setbErupt(0);
    }

    /**
     * Le déclenchement d'éruption spawn des TephraProjectile (animation
     * balistique) en début d'éruption. La couche BASALT n'apparaît qu'à
     * l'atterrissage (~30 ticks plus tard).
     */
    @Test
    void tephraSpawnProjectilesAuDebutEruption() {
        WorldOfCells world = buildWorld();
        int projectilesInit = countTephraProjectiles(world);
        LavaCA.setbErupt(1);
        world.lavaCA.step();
        int projectilesAfter = countTephraProjectiles(world);
        assertTrue(projectilesAfter > projectilesInit,
                "Au moins un TephraProjectile doit être spawné au premier step de l'éruption ; init="
                        + projectilesInit + ", after=" + projectilesAfter);
    }

    /**
     * Après ~60 ticks (assez pour l'animation TEPHRA_FLIGHT_TICKS=30 +
     * cleanup), les projectiles ont atterri → des couches BASALT doivent
     * apparaître autour du cratère.
     */
    @Test
    void tephraAtterritEtPoseBasalt() {
        WorldOfCells world = buildWorld();
        int basaltInit = countMaterial(world, Material.BASALT);
        LavaCA.setbErupt(1);
        // world.step() fait tourner stepAgents (qui avance les projectiles)
        // ET stepCellularAutomata tous les 10 ticks (qui déclenche ejectTephra
        // au tick 0 quand bErupt vient d'être posé à 1).
        for (int i = 0; i < 80; i++) world.step();
        int basaltAfter = countMaterial(world, Material.BASALT);
        assertTrue(basaltAfter > basaltInit,
                "Après 80 ticks, des couches BASALT doivent avoir été posées par les projectiles ; init="
                        + basaltInit + ", after=" + basaltAfter);
    }

    /** Sans déclencher d'éruption (bErupt=0, pErruption=0), aucun BASALT n'apparaît. */
    @Test
    void pasDeTephraSansEruption() {
        WorldOfCells world = buildWorld();
        int basaltInit = countMaterial(world, Material.BASALT);
        for (int i = 0; i < 200; i++) world.step();
        int basaltAfter = countMaterial(world, Material.BASALT);
        assertEquals(basaltInit, basaltAfter,
                "Sans éruption, aucun nouveau BASALT ne doit apparaître.");
        assertEquals(0, countTephraProjectiles(world),
                "Aucun projectile en vol non plus.");
    }

    /**
     * À la naissance d'une couche LAVA, si la cellule est dans rCratere,
     * la couche est marquée persistante.
     */
    @Test
    void laveAuCratereEstMarqueePersistante() {
        WorldOfCells world = buildWorld();
        int sx = LavaCA.sourceX, sy = LavaCA.sourceY;
        LavaCA.setbErupt(1);
        world.lavaCA.step();
        Layer top = world.topLayer(sx, sy);
        assertNotNull(top, "Une couche doit exister à la source après le 1er step.");
        assertEquals(Material.LAVA, top.material);
        assertTrue(top.persistent, "Couche LAVA à la source doit être marquée persistante.");
    }

    /**
     * À une distance > rCratere, la lave posée par propagation
     * n'est PAS marquée persistante.
     */
    @Test
    void laveHorsZonePersistanteNonMarquee() {
        WorldOfCells world = buildWorld();
        int sx = LavaCA.sourceX, sy = LavaCA.sourceY;
        // Force pressure = 1.0 pour déterminisme (sinon random ∈ [0.4, 2.0] peut donner
        // une éruption trop faible pour franchir le rim avec le modèle hydrostatique).
        world.lavaCA.erruptionPowerMin = 1.0f;
        world.lavaCA.erruptionPowerMax = 1.0f;
        LavaCA.setbErupt(1);
        // 60 steps : laisse l'éruption se développer + les projectiles LAVA atterrir
        // (TEPHRA_FLIGHT_TICKS = 30) + la propagation gravitaire s'étendre.
        for (int i = 0; i < 60; i++) world.lavaCA.step();
        int rPers = world.lavaCA.rCratere;
        // Scan global : cherche n'importe quelle cellule LAVA hors zone persistente.
        // Plus robuste que de tester des positions précises (projectiles LAVA + propagation
        // gravitaire ont une composante aléatoire).
        boolean foundNonPersistentLava = false;
        for (int cx = 0; cx < DX && !foundNonPersistentLava; cx++) {
            for (int cy = 0; cy < DY && !foundNonPersistentLava; cy++) {
                if (world.distance(cx, cy, sx, sy) <= rPers) continue;
                Layer l = world.topLayer(cx, cy);
                if (l != null && l.material == Material.LAVA && !l.persistent) {
                    foundNonPersistentLava = true;
                }
            }
        }
        assertTrue(foundNonPersistentLava,
                "Au moins une cellule LAVA hors zone persistante (rPers=" + rPers
                        + ") doit exister après 20 steps de propagation.");
    }

    /**
     * Une couche LAVA marquée `persistent=true` survit indéfiniment au
     * vieillissement (cap state = solidifyEnd-1, skip de replaceTopLayer).
     */
    @Test
    void laveMarqueePersistanteNeDurcitPas() {
        WorldOfCells world = buildWorld();
        world.lavaCA.subsidencePeriod = 0; // désactive subsidence pour isoler le test du flag persistent
        int cx = 25, cy = 25;
        world.pushLayer(cx, cy, Material.LAVA, 3f, 1);
        world.topLayer(cx, cy).persistent = true;
        for (int i = 0; i < 200; i++) world.lavaCA.step();
        Layer after = world.topLayer(cx, cy);
        assertNotNull(after);
        assertEquals(Material.LAVA, after.material,
                "LAVA persistante doit rester LAVA après 200 steps.");
        assertTrue(after.persistent, "Le flag survit aux ages successifs.");
    }

    /** Contrôle négatif : sans le flag, la lave durcit normalement après solidifyEnd. */
    @Test
    void laveNonMarqueeDurcit() {
        WorldOfCells world = buildWorld();
        int cx = 25, cy = 25;
        world.pushLayer(cx, cy, Material.LAVA, 3f, 1);
        assertFalse(world.topLayer(cx, cy).persistent,
                "Par défaut, persistent doit être false.");
        for (int i = 0; i < 200; i++) world.lavaCA.step();
        Layer after = world.topLayer(cx, cy);
        assertNotNull(after);
        assertNotEquals(Material.LAVA, after.material,
                "Sans flag persistent, la lave doit durcir en matériau minéral.");
    }

    /**
     * Une couche STONE isolée (aucun voisin solide) au-dessus d'une couche
     * LAVA fond progressivement et finit par disparaître.
     */
    @Test
    void pierreIsoleeFondDansLave() {
        WorldOfCells world = buildWorld();
        int cx = 10, cy = 10;
        world.pushLayer(cx, cy, Material.LAVA, 3f, 1);
        world.topLayer(cx, cy).persistent = true; // empêche durcissement parasite
        world.pushLayer(cx, cy, Material.STONE, 1f, 0);
        float thicknessInit = world.topLayer(cx, cy).thickness;

        // 0.95^60 ≈ 0.046, sous epsilon=0.05 → la couche est supprimée.
        for (int i = 0; i < 60; i++) world.lavaCA.step();
        Layer after = world.topLayer(cx, cy);
        boolean disappeared = (after != null && after.material == Material.LAVA);
        boolean shrunk = (after != null && after.material == Material.STONE
                && after.thickness < thicknessInit * 0.2f);
        assertTrue(disappeared || shrunk,
                "STONE isolée doit fondre. Après 60 steps : material="
                        + (after == null ? "null" : after.material)
                        + " thickness=" + (after == null ? "n/a" : after.thickness)
                        + " (initiale=" + thicknessInit + ")");
    }

    /**
     * Pont = au moins 2 voisins cardinaux solides ÉPAIS (≥ 1 bloc). Avec 2 STONE
     * voisins de 3.0 (= MIN_PROTECTIVE_THICKNESS) ET un top STONE également épais,
     * la STONE centrale est préservée. Cf. règles physiques 2026-05-27 :
     *  - Règle 1 : top fin (< 1 bloc) sur LAVA fond toujours
     *  - Règle 2 : voisins fins (< 1 bloc) ne comptent pas comme pont
     */
    @Test
    void pontProtegeContreLaFonte() {
        WorldOfCells world = buildWorld();
        int cx = 10, cy = 10;
        // Lave NON persistante (state=1 reste loin de solidifyEnd=150 sur 30 ticks)
        // → la solidification spontanée ne pollue pas le test du pont.
        world.pushLayer(cx, cy, Material.LAVA, 3f, 1);
        world.pushLayer(cx, cy, Material.STONE, 3f, 0);
        // Deux voisins cardinaux solides ÉPAIS → la STONE centrale est protégée.
        world.pushLayer(cx + 1, cy, Material.STONE, 3f, 0);
        world.pushLayer(cx - 1, cy, Material.STONE, 3f, 0);
        float thicknessInit = world.topLayer(cx, cy).thickness;

        for (int i = 0; i < 30; i++) world.lavaCA.step();
        Layer after = world.topLayer(cx, cy);
        assertEquals(Material.STONE, after.material);
        assertEquals(thicknessInit, after.thickness, 1e-6,
                "Avec 2 voisins STONE épais (pont) au-dessus de lave NON-persistante, la fonte ne s'applique pas.");
    }

    /**
     * Avec un seul voisin solide ÉPAIS, ce n'est pas considéré comme pont — la
     * pierre fond quand même (sémantique « bloc isolé »). Top et voisin tous ≥ 1 bloc
     * pour isoler le test des règles 1 (top fin) et 2 (voisin fin).
     */
    @Test
    void pierreAvecUnSeulVoisinFondQuandMeme() {
        WorldOfCells world = buildWorld();
        int cx = 10, cy = 10;
        world.pushLayer(cx, cy, Material.LAVA, 3f, 1);
        world.topLayer(cx, cy).persistent = true;
        world.pushLayer(cx, cy, Material.STONE, 3f, 0);
        // Un seul voisin solide épais.
        world.pushLayer(cx + 1, cy, Material.STONE, 3f, 0);
        float thicknessInit = world.topLayer(cx, cy).thickness;

        for (int i = 0; i < 60; i++) world.lavaCA.step();
        Layer after = world.topLayer(cx, cy);
        // Soit elle a fondu jusqu'à disparaître, soit elle est plus fine.
        boolean disappeared = (after != null && after.material == Material.LAVA);
        boolean shrunk = (after != null && after.material == Material.STONE
                && after.thickness < thicknessInit);
        assertTrue(disappeared || shrunk,
                "Avec 1 seul voisin solide épais, la pierre doit fondre. thickness avant="
                        + thicknessInit + ", après=" + (after == null ? "n/a" : after.thickness)
                        + " mat=" + (after == null ? "null" : after.material));
    }

    /** OBSIDIAN ne fond pas, même au-dessus de LAVA sans voisin solide. */
    @Test
    void obsidienneNeFondPas() {
        WorldOfCells world = buildWorld();
        int cx = 10, cy = 10;
        world.pushLayer(cx, cy, Material.LAVA, 3f, 1);
        world.topLayer(cx, cy).persistent = true;
        world.pushLayer(cx, cy, Material.OBSIDIAN, 1f, 0);
        float thicknessInit = world.topLayer(cx, cy).thickness;

        for (int i = 0; i < 30; i++) world.lavaCA.step();
        Layer after = world.topLayer(cx, cy);
        assertEquals(Material.OBSIDIAN, after.material);
        assertEquals(thicknessInit, after.thickness, 1e-6,
                "OBSIDIAN ne doit jamais fondre.");
    }

    private int countTephraProjectiles(WorldOfCells world) {
        int n = 0;
        for (objects.UniqueDynamicObject obj : world.uniqueDynamicObjects) {
            if (obj instanceof TephraProjectile) n++;
        }
        return n;
    }

    private int countMaterial(WorldOfCells world, Material m) {
        int count = 0;
        for (int x = 0; x < DX; x++) {
            for (int y = 0; y < DY; y++) {
                for (Layer l : world.getStackView(x, y)) {
                    if (l.material == m) count++;
                }
            }
        }
        return count;
    }

    // ═════════════════════════════════════════════════════════════════════
    //   Tests V2 — cratère creusé + lac de lave persistant + nouvelles règles
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Le creusement du trou est différé au premier step() pour disposer du
     * verticalScale (configuré par Landscape, absent en test). On le simule.
     */
    private WorldOfCells buildWorldWithVerticalScale() {
        WorldOfCells world = buildWorld();
        world.setVerticalScale(100.0f); // valeur prod : heightFactor=50 × heightBooster=2
        return world;
    }

    /** Après le premier step, la cellule centrale est plus basse qu'avant. */
    @Test
    void craterTrouCreuse() {
        WorldOfCells world = buildWorldWithVerticalScale();
        double heightAvant = world.getCellHeight(LavaCA.sourceX, LavaCA.sourceY);
        world.lavaCA.step(); // déclenche digHole() au premier appel
        double heightApres = world.getCellHeight(LavaCA.sourceX, LavaCA.sourceY);
        assertTrue(heightApres < heightAvant,
                "La cellule centrale du cratère doit être abaissée par digHole. avant="
                        + heightAvant + ", après=" + heightApres);
    }

    /**
     * Après une éruption complète (500 ticks) puis 1000 ticks sans nouvelle
     * éruption, la cellule centrale doit toujours avoir une couche LAVA au
     * sommet — la lave persistante ne solidifie jamais.
     */
    @Test
    void lacPersistantSurviveALongueDuree() {
        WorldOfCells world = buildWorldWithVerticalScale();
        LavaCA.setbErupt(1);
        for (int i = 0; i < 500; i++) world.lavaCA.step();   // durée éruption
        for (int i = 0; i < 1000; i++) world.lavaCA.step();  // post-éruption
        // Cherche la LAVA persistante dans la stack (elle doit toujours
        // exister). Boucle défensive — en pratique elle sera au sommet
        // puisque la subsidence ne pose plus de STONE par-dessus.
        boolean foundPersistentLava = false;
        for (Layer l : world.getStackView(LavaCA.sourceX, LavaCA.sourceY)) {
            if (l.material == Material.LAVA && l.persistent) {
                foundPersistentLava = true;
                break;
            }
        }
        assertTrue(foundPersistentLava,
                "La couche LAVA persistante doit subsister dans la stack centrale après "
                + "1500 ticks (500 éruption + 1000 post).");
    }

    /**
     * Lors d'une éruption, aucun TephraProjectile spawné ne doit cibler une
     * cellule à distance ≤ rCratere du centre.
     */
    @Test
    void tephraJamaisDansZonePersistante() {
        WorldOfCells world = buildWorldWithVerticalScale();
        LavaCA.setbErupt(1);
        world.lavaCA.step(); // déclenche ejectTephra()
        int rCraterePers = world.lavaCA.rCratere;
        int sx = LavaCA.sourceX, sy = LavaCA.sourceY;
        int violations = 0;
        for (objects.UniqueDynamicObject obj : world.uniqueDynamicObjects) {
            if (!(obj instanceof TephraProjectile)) continue;
            TephraProjectile p = (TephraProjectile) obj;
            if (world.distance(p.targetCellX, p.targetCellY, sx, sy) <= rCraterePers) {
                violations++;
            }
        }
        assertEquals(0, violations,
                "Aucun tephra ne doit atterrir dans la zone persistante (dist ≤ "
                        + rCraterePers + " de " + sx + "," + sy + ").");
    }

    /**
     * Nouvelle règle : une STONE posée sur de la lave PERSISTANTE fond
     * toujours, même avec 4 voisins solides (= pont théoriquement protecteur).
     */
    @Test
    void pierreSurLavePersistanteFondMemeAvecVoisins() {
        WorldOfCells world = buildWorld();
        int cx = 10, cy = 10;
        world.pushLayer(cx, cy, Material.LAVA, 3f, 1);
        world.topLayer(cx, cy).persistent = true;
        world.pushLayer(cx, cy, Material.STONE, 1f, 0);
        // 4 voisins cardinaux solides — normalement pont protecteur (>= 2).
        world.pushLayer(cx + 1, cy, Material.STONE, 1f, 0);
        world.pushLayer(cx - 1, cy, Material.STONE, 1f, 0);
        world.pushLayer(cx, cy + 1, Material.STONE, 1f, 0);
        world.pushLayer(cx, cy - 1, Material.STONE, 1f, 0);
        float thicknessInit = world.topLayer(cx, cy).thickness;

        for (int i = 0; i < 30; i++) world.lavaCA.step();
        Layer after = world.topLayer(cx, cy);
        boolean disappeared = (after != null && after.material == Material.LAVA);
        boolean shrunk = (after != null && after.material == Material.STONE
                && after.thickness < thicknessInit);
        assertTrue(disappeared || shrunk,
                "STONE sur lave persistante doit fondre malgré 4 voisins solides. mat="
                        + (after == null ? "null" : after.material) + ", thickness="
                        + (after == null ? "n/a" : after.thickness) + ", init=" + thicknessInit);
    }

    /**
     * Après la fin de l'éruption, la subsidence doit faire descendre la pile
     * de lave persistante. Avec subsidencePeriod=6 (défaut), une pile très
     * épaisse doit avoir perdu de l'épaisseur après 500 ticks.
     */
    @Test
    void subsidenceFaitDescendreLaPilePostEruption() {
        WorldOfCells world = buildWorld();
        int cx = 10, cy = 10;
        // Simule une grosse pile de lave persistante (état post-éruption).
        world.pushLayer(cx, cy, Material.LAVA, 50f, 1);
        world.topLayer(cx, cy).persistent = true;
        float thicknessInit = world.topLayer(cx, cy).thickness;

        // bErupt == 0 (post-éruption). subsidencePeriod défaut = 6.
        for (int i = 0; i < 500; i++) world.lavaCA.step();

        // Cherche la LAVA persistante dans la stack (devrait être au sommet
        // puisque la subsidence ne pose plus de STONE par-dessus).
        float lavaThicknessAfter = -1f;
        for (Layer l : world.getStackView(cx, cy)) {
            if (l.material == Material.LAVA && l.persistent) {
                lavaThicknessAfter = l.thickness;
                break;
            }
        }
        assertTrue(lavaThicknessAfter > 0, "La LAVA persistante doit toujours exister.");
        assertTrue(lavaThicknessAfter < thicknessInit,
                "La pile LAVA persistante doit avoir descendu. init=" + thicknessInit
                        + " après=" + lavaThicknessAfter);

        // Taux de descente attendu : delta=0.3 par période=6 ticks = 0.05/tick.
        // Sur 500 ticks, descente théorique max = 25 unités. On exige au moins
        // 50% du taux nominal pour garantir la mécanique de subsidence.
        float expectedDescentNominal = 0.05f * 500f;  // 25
        float minimalAcceptableDescent = expectedDescentNominal * 0.5f;  // 12.5
        float actualDescent = thicknessInit - lavaThicknessAfter;
        assertTrue(actualDescent >= minimalAcceptableDescent,
                "Taux de descente trop faible. attendu ≥ "
                        + minimalAcceptableDescent + " unités sur 500 ticks, obtenu "
                        + actualDescent);
    }

    /** La subsidence ne descend jamais en-dessous du plancher anchor (~3 unités). */
    @Test
    void subsidenceRespecteAnchorMinimum() {
        WorldOfCells world = buildWorld();
        int cx = 10, cy = 10;
        world.pushLayer(cx, cy, Material.LAVA, 50f, 1);
        world.topLayer(cx, cy).persistent = true;

        // Tourner très longtemps pour atteindre le plancher.
        for (int i = 0; i < 5000; i++) world.lavaCA.step();

        float lavaThickness = -1f;
        for (Layer l : world.getStackView(cx, cy)) {
            if (l.material == Material.LAVA && l.persistent) {
                lavaThickness = l.thickness;
                break;
            }
        }
        assertTrue(lavaThickness > 0, "Le LAC LAVA persistant doit subsister.");
        assertTrue(lavaThickness >= 3.0f - 0.5f,
                "La pile ne doit pas descendre sous le plancher anchor (~3). actuelle=" + lavaThickness);
    }

    /** Pendant l'éruption (au moins une dans activeEruptions), la subsidence ne s'applique pas. */
    @Test
    void subsidencePauseePendantEruption() {
        WorldOfCells world = buildWorld();
        int cx = 10, cy = 10;
        world.pushLayer(cx, cy, Material.LAVA, 50f, 1);
        world.topLayer(cx, cy).persistent = true;
        float thicknessInit = world.topLayer(cx, cy).thickness;

        LavaCA.setbErupt(1);
        // Module 2 : éruption dure config.eruptionDurationSec × simulationHz
        // = 5 × 20 = 100 ticks. On reste sous ce seuil pour tester la pause.
        for (int i = 0; i < 50; i++) world.lavaCA.step();

        // Ne doit PAS avoir de couche STONE liée à la subsidence par-dessus
        // notre LAVA persistante (le top doit rester LAVA, le push initial
        // ou les pushs de l'éruption ne mettent que de la LAVA).
        Layer top = world.topLayer(cx, cy);
        assertEquals(Material.LAVA, top.material,
                "Pendant l'éruption, pas de subsidence ⇒ pas de couche STONE par-dessus.");
        assertTrue(top.thickness >= thicknessInit,
                "Pendant l'éruption, la pile doit grossir ou rester stable, pas descendre.");
    }

    /** subsidencePeriod = 0 ⇒ la subsidence est totalement désactivée. */
    @Test
    void subsidenceDisabledSiPeriodZero() {
        WorldOfCells world = buildWorld();
        world.lavaCA.subsidencePeriod = 0; // disable
        int cx = 10, cy = 10;
        world.pushLayer(cx, cy, Material.LAVA, 50f, 1);
        world.topLayer(cx, cy).persistent = true;
        float thicknessInit = world.topLayer(cx, cy).thickness;

        for (int i = 0; i < 500; i++) world.lavaCA.step();

        Layer top = world.topLayer(cx, cy);
        assertEquals(Material.LAVA, top.material,
                "Sans subsidence, le top reste LAVA persistant indéfiniment.");
        assertEquals(thicknessInit, top.thickness, 1e-3,
                "Sans subsidence, la pile garde son épaisseur exacte.");
    }

    /** OBSIDIAN reste immune même au-dessus de lave persistante. */
    @Test
    void obsidienneAuDessusLavePersistanteResiste() {
        WorldOfCells world = buildWorld();
        int cx = 10, cy = 10;
        world.pushLayer(cx, cy, Material.LAVA, 3f, 1);
        world.topLayer(cx, cy).persistent = true;
        world.pushLayer(cx, cy, Material.OBSIDIAN, 1f, 0);
        // Pas de voisins solides — un STONE fondrait, mais OBSIDIAN doit tenir.
        float thicknessInit = world.topLayer(cx, cy).thickness;

        for (int i = 0; i < 30; i++) world.lavaCA.step();
        Layer after = world.topLayer(cx, cy);
        assertNotNull(after);
        assertEquals(Material.OBSIDIAN, after.material);
        assertEquals(thicknessInit, after.thickness, 1e-6,
                "OBSIDIAN ne doit jamais fondre, même au-dessus de lave persistante.");
    }

    /**
     * Sujet B : la subsidence doit retirer de l'épaisseur LAVA sans poser
     * de STONE par-dessus. Top de la cellule centrale doit rester LAVA après
     * N cycles de subsidence.
     */
    @Test
    void subsidenceNePosePlusDeStoneCrust() {
        WorldOfCells world = buildWorld();
        // Force le digHole et fait apparaître la LAVA persistante au cratère.
        LavaCA.setbErupt(1);
        world.lavaCA.step();
        LavaCA.setbErupt(0);
        // Pose manuellement une LAVA persistante épaisse pour avoir de la marge.
        int sx = LavaCA.sourceX;
        int sy = LavaCA.sourceY;
        // Vide la stack puis pose une LAVA épaisse marquée persistante.
        while (world.topLayer(sx, sy) != null) world.removeTopLayer(sx, sy);
        world.pushLayer(sx, sy, Material.LAVA, 10f, 0);
        Layer top = world.topLayer(sx, sy);
        top.persistent = true;
        // 20 cycles de subsidence (chacun retire 0.3 = total 6 unités).
        for (int i = 0; i < 20 * world.lavaCA.subsidencePeriod; i++) {
            world.lavaCA.step();
        }
        Layer finalTop = world.topLayer(sx, sy);
        assertNotNull(finalTop, "La stack ne doit pas être vide après subsidence");
        assertEquals(Material.LAVA, finalTop.material,
                "Le top doit rester LAVA après subsidence (sans crust STONE)");
    }

    // [SUPPRIMÉS lors de la refonte 2026-05 Module 2] :
    //   - lavePlafonneSurColonnePendantEruption : asserte le cap qui n'existe plus
    //   - propagationLateraleBloqueeTantQuePileSousRim : asserte la gate, supprimée
    //   - aucuneCelluleNeDepasseLeCapDecroissantPendantEruption : asserte le cap

    /**
     * Sujet D1 : à basse altitude (≤ 0.3 × maxH) et loin du cratère
     * (> 0.6 × rEff), chooseSolidifiedMaterial doit retourner 100% STONE.
     */
    @Test
    void chooseMaterialBasseAltitude100PourcentStone() {
        WorldOfCells world = buildWorld();
        double maxH = world.getMaxEverHeight();
        double height = maxH * 0.1;       // bas
        float rEff = 10f;
        int x = (LavaCA.sourceX + 8) % DX;   // distance ≈ 8 > 0.6×10 = 6
        int y = LavaCA.sourceY;
        int n = 1000;
        int stoneCount = 0;
        for (int i = 0; i < n; i++) {
            Material m = world.lavaCA.chooseSolidifiedMaterial(x, y, height, rEff);
            if (m == Material.STONE) stoneCount++;
        }
        assertEquals(n, stoneCount,
                "À basse altitude et loin du cratère, 100% STONE attendu ; obtenu " + stoneCount + "/" + n);
    }

    /**
     * Sujet D1 : à haute altitude (≥ 0.6 × maxH) et loin du cratère,
     * chooseSolidifiedMaterial doit retourner 100% GRANITE.
     */
    @Test
    void chooseMaterialHauteAltitude100PourcentGranite() {
        WorldOfCells world = buildWorld();
        double maxH = world.getMaxEverHeight();
        double height = maxH * 0.9;       // haut
        float rEff = 10f;
        int x = (LavaCA.sourceX + 8) % DX;
        int y = LavaCA.sourceY;
        int n = 1000;
        int graniteCount = 0;
        for (int i = 0; i < n; i++) {
            Material m = world.lavaCA.chooseSolidifiedMaterial(x, y, height, rEff);
            if (m == Material.GRANITE) graniteCount++;
        }
        assertEquals(n, graniteCount,
                "À haute altitude et loin du cratère, 100% GRANITE attendu ; obtenu " + graniteCount + "/" + n);
    }

    /**
     * Sujet D1 : au milieu de la zone de transition altitude (0.45 × maxH),
     * proportion granite ≈ 50% ± 10% sur 1000 tirages.
     */
    @Test
    void chooseMaterialZoneTransitionMix() {
        WorldOfCells world = buildWorld();
        double maxH = world.getMaxEverHeight();
        double height = maxH * 0.45;
        float rEff = 10f;
        int x = (LavaCA.sourceX + 8) % DX;
        int y = LavaCA.sourceY;
        int n = 1000;
        int graniteCount = 0;
        for (int i = 0; i < n; i++) {
            Material m = world.lavaCA.chooseSolidifiedMaterial(x, y, height, rEff);
            if (m == Material.GRANITE) graniteCount++;
        }
        // pGranite = (0.45 - 0.3) / 0.3 = 0.5. Tolérance ±10%.
        assertTrue(graniteCount >= 400 && graniteCount <= 600,
                "Au milieu de la zone mix, granite ∈ [40%, 60%] attendu ; obtenu " + graniteCount + "/" + n);
    }

    // ===== Module 3 — Tests World.pushLayer avec lineage =====

    @Test
    void pushLayerLavaSourcesDifferentesFusionneOptionB() {
        WorldOfCells world = buildWorld();
        LavaSource sA = new LavaSource(0, 0, 1, 0, 1.0f, 0.04f, 1.0f);
        LavaSource sB = new LavaSource(0, 0, 2, 0, 2.0f, -0.02f, 1.1f);

        int x = 25, y = 25;
        world.pushLayer(x, y, Material.LAVA, 3f, 1, LavaLineage.forSource(sA));
        world.pushLayer(x, y, Material.LAVA, 1f, 1, LavaLineage.forSource(sB));

        Layer top = world.topLayer(x, y);
        assertNotNull(top);
        assertNotNull(top.lineage);
        assertEquals(-1, top.lineage.source.eruptionId, "Sources différentes → mélange");
        // pressure attendu = (1.0*3 + 2.0*1) / 4 = 1.25
        assertEquals(1.25f, top.lineage.source.pressure, 1e-4f);
    }

    @Test
    void pushLayerLavaMemeSourceConserveLineage() {
        WorldOfCells world = buildWorld();
        LavaSource s = new LavaSource(0, 0, 1, 0, 1.0f, 0.04f, 1.0f);

        int x = 25, y = 25;
        world.pushLayer(x, y, Material.LAVA, 3f, 1, LavaLineage.forSource(s));
        world.pushLayer(x, y, Material.LAVA, 2f, 5, LavaLineage.forSource(s));

        Layer top = world.topLayer(x, y);
        assertNotNull(top.lineage);
        assertEquals(1, top.lineage.source.eruptionId, "Même source → conservée");
        assertEquals(5f, top.thickness, 1e-4f, "Épaisseurs additionnées");
    }

    @Test
    void pushLayerNonLavaSansLineageInchange() {
        WorldOfCells world = buildWorld();
        int x = 25, y = 25;
        world.pushLayer(x, y, Material.STONE, 2f, 0); // version legacy sans lineage
        Layer top = world.topLayer(x, y);
        assertNotNull(top);
        assertEquals(Material.STONE, top.material);
        assertEquals(2f, top.thickness, 1e-4f);
        assertNull(top.lineage, "STONE legacy n'a pas de lineage");
    }
}
