package agents;

import agents.ai.AgentState;
import agents.ai.Percept;
import agents.ai.Perception;
import agents.ai.SocialTrait;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase G — caractère social du Mouton (cf. docs/evolution.txt § 7) : la
 * satisfaction sociale dépend du caractère (un solitaire est satisfait seul), et
 * un mouton solitaire évite le troupeau.
 */
class MoutonCharacterTest {

    private static void makeSolitary(Mouton m) {
        for (int i = 0; i < 20; i++) m.character.observe(true, 0.8);
        m.character.endSession();
    }

    private static void makeGregarious(Mouton m) {
        for (int i = 0; i < 20; i++) m.character.observe(false, 0.8);
        m.character.endSession();
    }

    /** La satisfaction sociale dépend du caractère (§ 7.3/§ 7.5) : seuls, un
     *  solitaire est plus satisfait qu'un grégaire. */
    @Test
    void satisfactionSocialeDependDuCaractere() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        Mouton solitaire = new Mouton(10, 10, world);
        Mouton gregaire = new Mouton(40, 40, world);       // loin l'un de l'autre → isolés
        solitaire.energie = solitaire.energieMAX;
        gregaire.energie = gregaire.energieMAX;
        world.moutons.add(solitaire); world.moutons.add(gregaire);
        world.agents.add(solitaire); world.agents.add(gregaire);

        makeSolitary(solitaire);
        makeGregarious(gregaire);
        assertEquals(SocialTrait.SOLITARY, solitaire.character.social());
        assertEquals(SocialTrait.GREGARIOUS, gregaire.character.social());

        assertTrue(solitaire.satisfaction() > gregaire.satisfaction(),
                "seul, le solitaire est plus satisfait que le grégaire");
    }

    /** Un mouton SOLITAIRE n'intègre pas le troupeau la nuit (§ 7.2). */
    @Test
    void moutonSolitaireEviteLeTroupeauLaNuit() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        world.setJour(0);                                  // NUIT
        int cx = 25, cy = 25;
        AgentTestSupport.flattenLandArea(world, cx, cy, 11);  // terre déterministe
        Mouton m = new Mouton(cx, cy, world);
        m.energie = m.energieMAX * 0.7;
        world.moutons.add(m); world.agents.add(m);
        // troupeau autour pour offrir le regroupement
        for (int i = 1; i <= 3; i++) {
            Mouton f = new Mouton(cx + i, cy, world);
            world.moutons.add(f); world.agents.add(f);
        }
        makeSolitary(m);

        Percept p = Perception.sense(m, world, world.loups, null);
        assertNotEquals(AgentState.HERD, m.decideState(p),
                "un solitaire ne rejoint pas le troupeau");
    }

    /** Contrôle : un mouton GRÉGAIRE (ou neutre) rejoint bien le troupeau la nuit. */
    @Test
    void moutonGregaireRejointLeTroupeauLaNuit() {
        WorldOfCells world = AgentTestSupport.buildWorld();
        world.setJour(0);
        int cx = 25, cy = 25;
        AgentTestSupport.flattenLandArea(world, cx, cy, 11);  // terre déterministe
        Mouton m = new Mouton(cx, cy, world);
        m.energie = m.energieMAX * 0.7;
        world.moutons.add(m); world.agents.add(m);
        for (int i = 1; i <= 3; i++) {
            Mouton f = new Mouton(cx + i, cy, world);
            world.moutons.add(f); world.agents.add(f);
        }
        makeGregarious(m);

        Percept p = Perception.sense(m, world, world.loups, null);
        assertEquals(AgentState.HERD, m.decideState(p),
                "un grégaire rejoint le troupeau");
    }
}
