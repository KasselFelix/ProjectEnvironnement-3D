package agents;

import agents.ai.AgentState;
import agents.ai.LifeStage;
import agents.ai.Percept;
import agents.ai.Perception;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase B — cycle de vie du Mouton (cf. docs/evolution.txt § 10). Un fondateur
 * (spawné) est un ADULTE ; un agneau (né) démarre BÉBÉ et ne peut pas se
 * reproduire avant d'être adulte.
 */
class MoutonLifecycleTest {

    /** Un mouton fondateur (spawné) est ADULTE dès l'âge 0 ; un agneau (né) est
     *  BÉBÉ à l'âge 0. Le cycle bébé→jeune→adulte ne concerne que les nés. */
    @Test
    void fondateurEstAdulteAgneauEstBebe() {
        WorldOfCells world = AgentTestSupport.buildWorld();

        Mouton founder = new Mouton(20, 20, world);
        founder.maxAgeDays = 100.0;
        assertEquals(LifeStage.ADULT, founder.currentStage(),
                "un fondateur spawné est un adulte, pas un bébé");

        Mouton lamb = new Mouton(20, 20, world);
        lamb.maxAgeDays = 100.0;
        lamb.isFounder = false;                 // marqué comme NÉ
        assertEquals(LifeStage.BABY, lamb.currentStage(),
                "un agneau né à l'âge 0 est un bébé");
    }

    /** Un agneau BÉBÉ ne peut pas se reproduire, même énergie pleine + Prepro=1
     *  + partenaire à portée — alors qu'un fondateur ADULTE le peut (contrôle).
     *  Isole le gating par stade (§ 10.1). */
    @Test
    void bebeNeSeReproduitPasMaisAdulteOui() {
        // --- bébé : pas de reproduction ---
        WorldOfCells world = AgentTestSupport.buildWorld();
        Mouton baby = new Mouton(20, 20, world);
        baby.isFounder = false;                 // né → bébé à l'âge 0
        baby.energie = baby.energieMAX;
        baby.maxAgeDays = 100.0;
        baby.Prepro = 1.0;
        world.moutons.add(baby); world.agents.add(baby); world.uniqueDynamicObjects.add(baby);
        Mouton p1 = new Mouton(21, 20, world);
        p1.Prepro = 0;
        world.moutons.add(p1); world.agents.add(p1); world.uniqueDynamicObjects.add(p1);
        world.setNbmoutons(2);

        baby.step();
        assertEquals(2, world.moutons.size(), "un bébé ne se reproduit pas");

        // --- contrôle : adulte fondateur, même montage → naissance ---
        WorldOfCells world2 = AgentTestSupport.buildWorld();
        Mouton adult = new Mouton(20, 20, world2);   // fondateur = adulte
        adult.energie = adult.energieMAX;
        adult.maxAgeDays = 100.0;
        adult.Prepro = 1.0;
        world2.moutons.add(adult); world2.agents.add(adult); world2.uniqueDynamicObjects.add(adult);
        Mouton p2 = new Mouton(21, 20, world2);
        p2.Prepro = 0;
        world2.moutons.add(p2); world2.agents.add(p2); world2.uniqueDynamicObjects.add(p2);
        world2.setNbmoutons(2);

        adult.step();
        assertEquals(3, world2.moutons.size(), "un adulte se reproduit (contrôle)");
    }

    /** L'agneau hérite du sizeFactor MOYENNÉ des deux parents + mutation, clampé
     *  [0.8, 1.2] (§ 10.2). Parents à 1.2 → agneau strictement > 1.0 (donc bien
     *  hérité, pas la valeur par défaut 1.0) et ≤ 1.2 (clamp). */
    @Test
    void agneauHeriteSizeFactorMoyenneAvecMutation() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        Mouton m = new Mouton(20, 20, world);
        m.sizeFactor = 1.2;
        m.energie = m.energieMAX;
        m.maxAgeDays = 100.0;
        m.Prepro = 1.0;
        world.moutons.add(m); world.agents.add(m); world.uniqueDynamicObjects.add(m);
        Mouton partner = new Mouton(21, 20, world);
        partner.sizeFactor = 1.2;
        partner.Prepro = 0;
        world.moutons.add(partner); world.agents.add(partner); world.uniqueDynamicObjects.add(partner);
        world.setNbmoutons(2);

        m.step();

        assertEquals(3, world.moutons.size());
        Mouton lamb = world.moutons.get(2);
        assertTrue(lamb.sizeFactor > 1.0, "sizeFactor hérité de parents à 1.2, pas le défaut 1.0");
        assertTrue(lamb.sizeFactor <= 1.2 + 1e-9, "clampé à 1.2 max");
    }

    /** growthScale() (§ 10.2) : un fondateur est à pleine taille (1.0) ; un
     *  agneau né (âge 0) démarre à la fraction de naissance (~0.40). */
    @Test
    void echelleDeCroissanceFondateurVsAgneau() {
        WorldOfCells world = AgentTestSupport.buildWorld();

        Mouton founder = new Mouton(20, 20, world);
        founder.maxAgeDays = 100.0;
        assertEquals(1.0, founder.growthScale(), 1e-9, "un fondateur est à pleine taille");

        Mouton lamb = new Mouton(20, 20, world);
        lamb.maxAgeDays = 100.0;
        lamb.isFounder = false;                 // né → bébé à l'âge 0
        assertEquals(0.40, lamb.growthScale(), 0.02, "un nouveau-né démarre à ~40 %");
    }

    /** Un agneau (BÉBÉ) avec un parent vivant à portée décide de le SUIVRE
     *  (§ 10.3), priorité au-dessus de l'errance. */
    @Test
    void agneauSuitSonParent() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int[] land = AgentTestSupport.findLandCell(world, 25, 25);

        Mouton parent = new Mouton(land[0], land[1], world);
        world.moutons.add(parent); world.agents.add(parent);

        Mouton lamb = new Mouton(land[0], land[1], world);  // même cellule (terre)
        lamb.isFounder = false;                  // bébé
        lamb.maxAgeDays = 100.0;
        lamb.energie = lamb.energieMAX * 0.8;    // ni affamé ni au repos
        lamb.parent = parent;
        world.moutons.add(lamb); world.agents.add(lamb);

        Percept p = Perception.sense(lamb, world, world.loups, null);
        assertEquals(AgentState.FOLLOW_PARENT, lamb.decideState(p));
    }

    /** En état FOLLOW_PARENT, l'agneau s'oriente VERS son parent (§ 10.3). */
    @Test
    void agneauSOrienteVersSonParent() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        Mouton parent = new Mouton(30, 20, world);     // à l'est de l'agneau
        Mouton lamb = new Mouton(20, 20, world);
        lamb.isFounder = false;
        lamb.parent = parent;
        world.moutons.add(parent); world.moutons.add(lamb);
        world.agents.add(parent); world.agents.add(lamb);

        Percept p = Perception.sense(lamb, world, world.loups, null);
        lamb.applyState(AgentState.FOLLOW_PARENT, p);

        int expected = Perception.dirToCell(lamb, world, parent.x, parent.y);
        assertEquals(expected, lamb._orient, "l'agneau doit viser la direction du parent");
    }

    /** À sizeFactor et stade égaux, un mouton FORT a une taille de rendu plus
     *  grande qu'un mouton neutre (§ 10.2 : la Force tire la taille). */
    @Test
    void moutonFortEstPlusGrandQuUnNeutre() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        Mouton neutre = new Mouton(20, 20, world);
        Mouton fort = new Mouton(20, 20, world);
        fort.genome.set(agents.ai.Axis.STRENGTH, agents.ai.Pole.POSITIVE);
        assertTrue(fort.displaySize() > neutre.displaySize(),
                "un mouton FORT doit avoir une taille de rendu supérieure");
    }

    /** Un orphelin (parent mort) ne suit personne → n'est pas en FOLLOW_PARENT. */
    @Test
    void agneauOrphelinNeSuitPersonne() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        int[] land = AgentTestSupport.findLandCell(world, 25, 25);
        Mouton parent = new Mouton(land[0], land[1], world);
        parent._alive = false;                   // parent mort
        Mouton lamb = new Mouton(land[0], land[1], world);
        lamb.isFounder = false;
        lamb.maxAgeDays = 100.0;
        lamb.energie = lamb.energieMAX * 0.8;
        lamb.parent = parent;
        world.moutons.add(lamb); world.agents.add(lamb);

        Percept p = Perception.sense(lamb, world, world.loups, null);
        assertNotEquals(AgentState.FOLLOW_PARENT, lamb.decideState(p));
    }
}
