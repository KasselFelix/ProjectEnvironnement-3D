package worlds;

import java.util.ArrayList;
import java.util.List;

/**
 * Journal d'événements de la simulation (V6) : compteurs cumulés (arbres brûlés,
 * agents morts, volume de lave émis) et file de NOTIFICATIONS transitoires
 * (« 12 moutons morts ! ») horodatées à l'itération, que l'UI affiche en fondu
 * puis purge. Données pures (aucune dépendance OpenGL) — la couche `ui` lit, la
 * simulation écrit.
 */
public class EventLog {

    /** Nombre cumulé d'arbres partis en flammes depuis le début. */
    public int treesBurned = 0;
    /** Nombre cumulé d'agents morts (toutes espèces, toutes causes). */
    public int agentDeaths = 0;
    /** Volume cumulé de lave injectée au cratère (unités d'épaisseur). */
    public long lavaEmitted = 0;

    /** Une notification transitoire : message + itération d'apparition. */
    public static final class Notif {
        public final String message;
        public final int iteration;
        public Notif(String message, int iteration) {
            this.message = message;
            this.iteration = iteration;
        }
    }

    private final List<Notif> notifications = new ArrayList<>();

    /** Enregistre une notification à afficher (V6). */
    public void notify(String message, int iteration) {
        notifications.add(new Notif(message, iteration));
    }

    /** Notifications encore « vivantes » à {@code currentIteration} (âge &lt;
     *  {@code ttlTicks}), de la plus récente à la plus ancienne. Purge au passage
     *  celles qui ont expiré. */
    public List<Notif> activeNotifications(int currentIteration, int ttlTicks) {
        notifications.removeIf(n -> currentIteration - n.iteration >= ttlTicks);
        List<Notif> out = new ArrayList<>(notifications);
        java.util.Collections.reverse(out);
        return out;
    }

    public int notificationCount() { return notifications.size(); }
}
