package agents.ai;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase C du système d'évolution (cf. docs/evolution.txt § 5) : mémoire de
 * CONNAISSANCES (points d'eau, zones de chasse/danger, lieux sûrs) à capacité
 * variable, avec éviction par priorité (provisoire avant consolidé, FOOD avant
 * survie, puis usage) lorsqu'elle déborde.
 */
class SemanticMemoryTest {

    /** Retenir un lieu puis le restituer : il est mémorisé et compté. */
    @Test
    void retientEtRestitueUnLieu() {
        SemanticMemory mem = new SemanticMemory();
        mem.remember(MemoryKind.WATER, 5, 5);

        assertEquals(1, mem.size());
        assertTrue(mem.contains(MemoryKind.WATER, 5, 5));
        assertFalse(mem.contains(MemoryKind.WATER, 9, 9));
    }

    /** Re-mémoriser le même lieu ne crée pas de doublon (il renforce l'usage). */
    @Test
    void memoriserLeMemeLieuNeDupliquePas() {
        SemanticMemory mem = new SemanticMemory();
        mem.remember(MemoryKind.WATER, 5, 5);
        mem.remember(MemoryKind.WATER, 5, 5);
        assertEquals(1, mem.size());
        assertEquals(2, mem.usageOf(MemoryKind.WATER, 5, 5), "usage renforcé à chaque rappel");
    }

    /** Éviction (§ 5.2) : à capacité pleine, entre souvenirs de même catégorie/
     *  consolidation, l'arrivée d'un nouveau souvenir évince le moins utilisé. */
    @Test
    void oublieLeMoinsUtiliseQuandPlein() {
        SemanticMemory mem = new SemanticMemory();
        mem.setCapacity(2);
        mem.remember(MemoryKind.WATER, 1, 1);     // usage 1
        mem.remember(MemoryKind.WATER, 2, 2);     // usage 1
        mem.remember(MemoryKind.WATER, 1, 1);     // (1,1) usage 2 → (2,2) est le moins utilisé
        mem.remember(MemoryKind.WATER, 3, 3);     // plein → évince (2,2)

        assertEquals(2, mem.size());
        assertTrue(mem.contains(MemoryKind.WATER, 1, 1), "le plus utilisé reste");
        assertTrue(mem.contains(MemoryKind.WATER, 3, 3), "le nouveau entre");
        assertFalse(mem.contains(MemoryKind.WATER, 2, 2), "le moins utilisé est oublié");
    }

    /** nearest() rend le lieu mémorisé le plus proche de la catégorie demandée,
     *  selon une fonction de distance injectée ; null si la catégorie est vide. */
    @Test
    void trouveLeLieuMemoriseLePlusProche() {
        SemanticMemory mem = new SemanticMemory();
        SemanticMemory.Distance euclid =
                (x1, y1, x2, y2) -> Math.hypot(x1 - x2, y1 - y2);

        assertNull(mem.nearest(MemoryKind.WATER, 0, 0, euclid), "aucun souvenir → null");

        mem.remember(MemoryKind.WATER, 5, 5);
        mem.remember(MemoryKind.WATER, 10, 10);
        mem.remember(MemoryKind.DANGER, 6, 6);     // autre catégorie : ignorée

        int[] near = mem.nearest(MemoryKind.WATER, 6, 6, euclid);
        assertArrayEquals(new int[]{5, 5}, near, "le point d'eau le plus proche de (6,6)");
    }

    /** Capacité variable (§ 5.1) : un intelligent retient plus qu'un neutre ; un
     *  vieux retient moins qu'un jeune ; jamais sous un plancher. */
    @Test
    void capaciteVariableSelonGenomeEtAge() {
        Genome neutre = new Genome();
        Genome intelligent = new Genome();
        intelligent.set(Axis.INTELLIGENCE, Pole.POSITIVE);

        int capNeutre = SemanticMemory.capacityFor(neutre, 0.0, 100.0);
        int capIntelligent = SemanticMemory.capacityFor(intelligent, 0.0, 100.0);
        assertTrue(capIntelligent > capNeutre, "l'intelligent retient davantage");

        int capJeune = SemanticMemory.capacityFor(neutre, 0.0, 100.0);
        int capVieux = SemanticMemory.capacityFor(neutre, 90.0, 100.0);
        assertTrue(capVieux < capJeune, "le vieux retient moins (dégénérescence § 5.2)");

        assertTrue(SemanticMemory.capacityFor(neutre, 100000.0, 100.0) >= 2,
                "la capacité ne descend jamais sous le plancher");
    }

    /** teach() transmet tous les souvenirs d'une mémoire à une autre (§ 8) :
     *  l'élève acquiert ce que le maître connaît et qu'il ignorait. */
    @Test
    void enseignerTransmetLesConnaissances() {
        SemanticMemory maitre = new SemanticMemory();
        maitre.remember(MemoryKind.SAFE_PLACE, 7, 7);
        maitre.remember(MemoryKind.DANGER, 3, 3);

        SemanticMemory eleve = new SemanticMemory();
        assertFalse(eleve.contains(MemoryKind.SAFE_PLACE, 7, 7));

        maitre.teach(eleve);

        assertTrue(eleve.contains(MemoryKind.SAFE_PLACE, 7, 7), "lieu sûr appris");
        assertTrue(eleve.contains(MemoryKind.DANGER, 3, 3), "danger appris");
    }

    /** Une zone de chasse répétitivement stérile finit par être oubliée (décote). */
    @Test
    void zoneDeChasseSterileEstOubliee() {
        SemanticMemory mem = new SemanticMemory();
        SemanticMemory.Distance d = (x1, y1, x2, y2) -> Math.hypot(x1 - x2, y1 - y2);
        mem.remember(MemoryKind.HUNTING, 10, 10);

        // 2 visites stériles (seuil 3) : pas encore oublié.
        assertFalse(mem.recordSterileVisit(MemoryKind.HUNTING, 10, 10, 3, d, 3));
        assertFalse(mem.recordSterileVisit(MemoryKind.HUNTING, 10, 10, 3, d, 3));
        assertTrue(mem.contains(MemoryKind.HUNTING, 10, 10));
        // 3e visite : seuil atteint → oublié.
        assertTrue(mem.recordSterileVisit(MemoryKind.HUNTING, 10, 10, 3, d, 3));
        assertFalse(mem.contains(MemoryKind.HUNTING, 10, 10));
    }

    /** Sans souvenir de chasse proche, une visite stérile est sans effet. */
    @Test
    void visiteSterileSansSouvenirEstSansEffet() {
        SemanticMemory mem = new SemanticMemory();
        SemanticMemory.Distance d = (x1, y1, x2, y2) -> Math.hypot(x1 - x2, y1 - y2);
        assertFalse(mem.recordSterileVisit(MemoryKind.HUNTING, 10, 10, 3, d, 3));
    }

    /** Revoir une proie (remember) réhabilite la zone : son compteur d'échecs repart de zéro. */
    @Test
    void observationRehabiliteLaZoneDeChasse() {
        SemanticMemory mem = new SemanticMemory();
        SemanticMemory.Distance d = (x1, y1, x2, y2) -> Math.hypot(x1 - x2, y1 - y2);
        mem.remember(MemoryKind.HUNTING, 10, 10);
        mem.recordSterileVisit(MemoryKind.HUNTING, 10, 10, 3, d, 3);
        mem.recordSterileVisit(MemoryKind.HUNTING, 10, 10, 3, d, 3);   // 2/3 échecs
        mem.remember(MemoryKind.HUNTING, 10, 10);                       // proie revue → reset

        // Il faut de nouveau 3 visites stériles complètes pour l'oublier.
        assertFalse(mem.recordSterileVisit(MemoryKind.HUNTING, 10, 10, 3, d, 3));
        assertFalse(mem.recordSterileVisit(MemoryKind.HUNTING, 10, 10, 3, d, 3));
        assertTrue(mem.contains(MemoryKind.HUNTING, 10, 10), "réhabilitée → toujours mémorisée");
        assertTrue(mem.recordSterileVisit(MemoryKind.HUNTING, 10, 10, 3, d, 3));   // 3e → oubli
        assertFalse(mem.contains(MemoryKind.HUNTING, 10, 10));
    }
}
