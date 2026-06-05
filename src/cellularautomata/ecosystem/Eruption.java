package cellularautomata.ecosystem;

/**
 * État d'une éruption active (Module 2 — refonte 2026-05). Une nouvelle
 * Eruption est créée à chaque trigger (touche `r` ou pErruption spontanée)
 * et stockée dans `LavaCA.activeEruptions`. Décrémentée à chaque tick CA
 * jusqu'à `ticksRemaining == 0` (chute de pression, éruption fermée), après quoi elle reste
 * encore dans la liste tant qu'il existe au moins un bloc LAVA descendant
 * (lineage.source == this.source).
 *
 * Plusieurs Eruptions peuvent coexister (spam de `r`) — chacune injecte
 * son propre flux au cratère, avec sa propre LavaSource (donc sa propre
 * signature couleur).
 */
public class Eruption {

    public final int eruptionId;       // ordinal, incrémenté à chaque éruption
    public final int sourceX, sourceY; // épicentre
    public final float pressure;       // tirée dans [pMin, pMax] au trigger
    public int ticksRemaining;         // décrémente à chaque tick, 0 = chute de pression
    public final int birthTick;        // tick global de naissance
    public final LavaSource source;    // partagée par tous les blocs descendants

    public Eruption(int eruptionId, int sourceX, int sourceY, float pressure,
                    int ticksRemaining, int birthTick) {
        this.eruptionId = eruptionId;
        this.sourceX = sourceX;
        this.sourceY = sourceY;
        this.pressure = pressure;
        this.ticksRemaining = ticksRemaining;
        this.birthTick = birthTick;
        this.source = LavaSource.forEruption(sourceX, sourceY, eruptionId, birthTick, pressure);
    }

    /** Robinet ouvert : injection active au cratère. */
    public boolean isOpen() {
        return ticksRemaining > 0;
    }
}
