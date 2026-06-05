package agents.ai;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase G du système d'évolution (cf. docs/evolution.txt § 7) : le caractère
 * social est ÉMERGENT — gagné par session si le comportement est soutenu ET la
 * satisfaction haute, perdu si le comportement change OU si la satisfaction
 * s'effondre (hystérésis pour éviter le yo-yo).
 */
class CharacterTest {

    private static void observe(Character c, boolean isolated, double sat, int n) {
        for (int i = 0; i < n; i++) c.observe(isolated, sat);
    }

    /** Aucun caractère à la naissance (§ 7). */
    @Test
    void aucunCaractereAuDepart() {
        assertEquals(SocialTrait.NONE, new Character().social());
    }

    /** Gain SOLITAIRE (§ 7.4) : isolé > 50 % du temps ET satisfaction haute. */
    @Test
    void devientSolitaireSiIsoleEtSatisfait() {
        Character c = new Character();
        observe(c, true, 0.8, 20);
        c.endSession();
        assertEquals(SocialTrait.SOLITARY, c.social());
    }

    /** Gain GRÉGAIRE : en groupe la plupart du temps ET satisfaction haute. */
    @Test
    void devientGregaireSiEnGroupeEtSatisfait() {
        Character c = new Character();
        observe(c, false, 0.8, 20);
        c.endSession();
        assertEquals(SocialTrait.GREGARIOUS, c.social());
    }

    /** Pas de caractère si la satisfaction reste basse, même comportement soutenu. */
    @Test
    void pasDeCaractereSiSatisfactionBasse() {
        Character c = new Character();
        observe(c, true, 0.4, 20);   // isolé mais peu satisfait
        c.endSession();
        assertEquals(SocialTrait.NONE, c.social());
    }

    /** Perte par satisfaction effondrée (§ 7.4 b / § 7.5) : un SOLITAIRE
     *  malheureux « se remet en question » et abandonne le trait. */
    @Test
    void perdLeCaractereSiSatisfactionTropBasse() {
        Character c = new Character();
        observe(c, true, 0.8, 20);
        c.endSession();
        assertEquals(SocialTrait.SOLITARY, c.social());

        observe(c, true, 0.2, 20);   // toujours isolé mais misérable
        c.endSession();
        assertEquals(SocialTrait.NONE, c.social(), "satisfaction trop basse → abandon");
    }

    /** Hystérésis (§ 7.4) : satisfaction dans la bande morte (entre S_LOSE et
     *  S_GAIN) → le caractère est CONSERVÉ (pas de yo-yo). */
    @Test
    void conserveLeCaractereDansLaBandeMorte() {
        Character c = new Character();
        observe(c, true, 0.8, 20);
        c.endSession();                              // devient SOLITAIRE

        observe(c, true, 0.45, 20);                  // entre S_LOSE et S_GAIN
        c.endSession();
        assertEquals(SocialTrait.SOLITARY, c.social(), "bande morte → conservé");
    }

    /** Perte par changement de comportement (§ 7.4 a) : un SOLITAIRE qui finit
     *  par vivre en groupe perd son trait (l'individu a changé de vie). */
    @Test
    void perdLeCaractereSiLeComportementChange() {
        Character c = new Character();
        observe(c, true, 0.8, 20);
        c.endSession();                              // SOLITAIRE

        observe(c, false, 0.8, 20);                  // vit en groupe désormais
        c.endSession();
        assertEquals(SocialTrait.NONE, c.social(), "comportement changé → trait perdu");
    }
}
