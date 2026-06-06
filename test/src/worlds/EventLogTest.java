package worlds;

import agents.AgentTestSupport;
import agents.Mouton;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Journal d'événements & feedback (V6) : compteur de morts d'agents +
 * notifications transitoires, et purge par TTL. Pas de dépendance OpenGL.
 */
class EventLogTest {

    @Test
    void laMortDUnMoutonEstCompteeEtNotifiee() {
        WorldOfCells w = AgentTestSupport.buildWorld();
        int[] c = AgentTestSupport.findLandCell(w, 25, 25);
        Mouton m = new Mouton(c[0], c[1], w);
        w.moutons.add(m);
        w.agents.add(m);
        w.uniqueDynamicObjects.add(m);

        assertEquals(0, w.events.agentDeaths);
        m._alive = false;     // mort (cause quelconque)
        w.step();             // stepAgents purge le mort

        assertEquals(1, w.events.agentDeaths, "la mort de l'agent est comptée");
        assertTrue(w.events.notificationCount() >= 1, "une notification est émise");
    }

    @Test
    void lesNotificationsExpirentParTTL() {
        EventLog log = new EventLog();
        log.notify("test", 100);
        assertEquals(1, log.activeNotifications(110, 50).size(), "encore vivante à +10 (<TTL 50)");
        assertEquals(0, log.activeNotifications(200, 50).size(), "expirée à +100 (>=TTL 50)");
        assertEquals(0, log.notificationCount(), "la purge a retiré la notif expirée");
    }

    @Test
    void notificationsRenduesDeLaPlusRecenteALaPlusAncienne() {
        EventLog log = new EventLog();
        log.notify("ancienne", 10);
        log.notify("recente", 20);
        java.util.List<EventLog.Notif> ns = log.activeNotifications(25, 100);
        assertEquals("recente", ns.get(0).message, "la plus récente d'abord");
        assertEquals("ancienne", ns.get(1).message);
    }
}
