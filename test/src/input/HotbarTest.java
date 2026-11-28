package input;

import agents.Loup;
import org.junit.jupiter.api.Test;
import worlds.WorldOfCells;
import static org.junit.jupiter.api.Assertions.*;

class HotbarTest {
    private static WorldOfCells flat() {
        WorldOfCells w = agents.AgentTestSupport.buildWorld();
        int W = w.getWidth(), H = w.getHeight();
        for (int x = 0; x < W; x++) for (int y = 0; y < H; y++) { w.setCellHeight(x, y, 0.5); w.setForestCAValue(x, y, 0); }
        return w;
    }

    @Test
    void mangerDisponibleSeulementSiCarcasseAdjacente() {
        WorldOfCells w = flat();
        Loup l = new Loup(10, 10, w); w.loups.add(l);
        assertFalse(HotbarAction.MANGER.available(l), "pas de carcasse => grise");
        w.spawnCarcass(11, 10, 70.0, objects.Species.MOUTON);
        assertTrue(HotbarAction.MANGER.available(l), "carcasse adjacente + cooldown 0 => dispo");
    }

    @Test
    void hurlerSuitLeCooldown() {
        WorldOfCells w = flat();
        Loup l = new Loup(10, 10, w); w.loups.add(l);
        assertTrue(HotbarAction.HURLER.available(l), "cooldown initial 0 => dispo");
        assertFalse(HotbarAction.HURLER.available(new agents.Mouton(5, 5, w)), "pas un loup => indisponible");
    }

    @Test
    void actionsNonImplementeesToujoursGrisees() {
        WorldOfCells w = flat();
        Loup l = new Loup(10, 10, w); w.loups.add(l);
        assertFalse(HotbarAction.FRAPPER.isImplemented());
        assertFalse(HotbarAction.FRAPPER.available(l), "non implementee => jamais disponible");
    }

    @org.junit.jupiter.api.Test
    void layoutsParDefautParEspece() {
        HotbarLayout h = new HotbarLayout();
        assertEquals(HotbarAction.FRAPPER, h.slot(objects.Species.LOUP, 0));
        assertEquals(HotbarAction.MANGER,  h.slot(objects.Species.LOUP, 1));
        assertEquals(HotbarAction.HURLER,  h.slot(objects.Species.LOUP, 2));
        assertEquals(HotbarAction.VIDE,    h.slot(objects.Species.LOUP, 8));
        assertEquals(HotbarAction.MANGER,  h.slot(objects.Species.OURS, 1));
        assertEquals(HotbarAction.BROUTER, h.slot(objects.Species.MOUTON, 0));
    }

    @org.junit.jupiter.api.Test
    void assignationEtRoundTripProperties() {
        HotbarLayout h = new HotbarLayout();
        h.assign(objects.Species.LOUP, 4, HotbarAction.SE_REPOSER);
        java.util.Properties p = new java.util.Properties();
        h.writeTo(p);
        HotbarLayout h2 = new HotbarLayout();
        h2.readFrom(p);
        assertEquals(HotbarAction.SE_REPOSER, h2.slot(objects.Species.LOUP, 4));
        assertEquals(HotbarAction.FRAPPER,    h2.slot(objects.Species.LOUP, 0), "slots non touches = defauts");
    }

    @Test
    void odoratPassifActiveParDefautEtBascule() {
        WorldOfCells w = flat();
        Loup l = new Loup(10, 10, w); w.loups.add(l);
        agents.Ours o = new agents.Ours(12, 12, w);
        agents.Mouton m = new agents.Mouton(5, 5, w);
        assertTrue(HotbarAction.ODORAT.isToggle());
        assertTrue(HotbarAction.ODORAT.available(l) && HotbarAction.ODORAT.available(o), "loup et ours ont l'odorat");
        assertFalse(HotbarAction.ODORAT.available(m), "le mouton n'a pas l'odorat developpe");
        assertTrue(HotbarAction.ODORAT.isToggledOn(l), "active par defaut");
        HotbarAction.ODORAT.execute(l);
        assertFalse(HotbarAction.ODORAT.isToggledOn(l), "un appui l'eteint");
        assertFalse(l.playerWantsEat || l.playerWantsHowl, "aucune action de tour posee");
        HotbarAction.ODORAT.execute(l);
        assertTrue(HotbarAction.ODORAT.isToggledOn(l), "un second appui le rallume");
        assertFalse(HotbarAction.MANGER.isToggle());
    }

    @Test
    void odoratDansLaBarreParDefautDuLoupEtDeLOurs() {
        HotbarLayout h = new HotbarLayout();
        assertEquals(HotbarAction.ODORAT, h.slot(objects.Species.LOUP, 4));
        assertEquals(HotbarAction.ODORAT, h.slot(objects.Species.OURS, 3));
    }

    @Test
    void ancienneSauvegardeRecoitOdoratAuPremierSlotLibre() {
        // Sauvegarde d'avant ODORAT (pas de hotbar.version) : 9 slots écrits, dont VIDE.
        java.util.Properties p = new java.util.Properties();
        String[] loup = {"FRAPPER", "MANGER", "HURLER", "SE_REPOSER", "VIDE", "VIDE", "VIDE", "VIDE", "VIDE"};
        for (int i = 0; i < 9; i++) p.setProperty("hotbar.LOUP." + i, loup[i]);
        p.setProperty("hotbar.LOUP.4", "BOIRE");   // le joueur avait rempli le slot 5
        HotbarLayout h = new HotbarLayout();
        h.readFrom(p);
        assertEquals(HotbarAction.BOIRE,  h.slot(objects.Species.LOUP, 4), "choix du joueur garde");
        assertEquals(HotbarAction.ODORAT, h.slot(objects.Species.LOUP, 5), "Odorat au premier slot libre");

        // Une fois sauvee en version 2, retirer Odorat est respecte.
        h.assign(objects.Species.LOUP, 5, HotbarAction.VIDE);
        java.util.Properties p2 = new java.util.Properties();
        h.writeTo(p2);
        HotbarLayout h2 = new HotbarLayout();
        h2.readFrom(p2);
        assertEquals(HotbarAction.VIDE, h2.slot(objects.Species.LOUP, 5), "retrait respecte apres migration");
    }

    @Test
    void marcherPassifEteintParDefautEtBascule() {
        WorldOfCells w = flat();
        Loup l = new Loup(10, 10, w); w.loups.add(l);
        agents.Mouton m = new agents.Mouton(5, 5, w);
        assertTrue(HotbarAction.MARCHER.isToggle());
        assertTrue(HotbarAction.MARCHER.available(l), "le loup a un pas distinct du trot");
        assertFalse(HotbarAction.MARCHER.available(m), "le mouton n'a que deux allures");
        assertFalse(HotbarAction.MARCHER.isToggledOn(l), "eteint par defaut");
        HotbarAction.MARCHER.execute(l);
        assertTrue(HotbarAction.MARCHER.isToggledOn(l));
        HotbarAction.MARCHER.execute(l);
        assertFalse(HotbarAction.MARCHER.isToggledOn(l));
    }

    @Test
    void sauvegardeVersion2RecoitMarcherSansToucherOdorat() {
        java.util.Properties p = new java.util.Properties();
        String[] loup = {"FRAPPER", "MANGER", "HURLER", "SE_REPOSER", "VIDE", "ODORAT", "VIDE", "VIDE", "VIDE"};
        for (int i = 0; i < 9; i++) p.setProperty("hotbar.LOUP." + i, loup[i]);
        p.setProperty("hotbar.version", "2");
        HotbarLayout h = new HotbarLayout();
        h.readFrom(p);
        assertEquals(HotbarAction.MARCHER, h.slot(objects.Species.LOUP, 4), "Marcher au premier slot libre");
        assertEquals(HotbarAction.ODORAT,  h.slot(objects.Species.LOUP, 5), "Odorat reste ou le joueur l'a mis");
        assertEquals(HotbarAction.VIDE,    h.slot(objects.Species.LOUP, 6));
    }
}
