package ui;

import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.Set;

/**
 * Holder partagé pour l'état de la souris et du clavier sur la frame courante.
 * Alimenté par les MouseListener / KeyListener de `Landscape` (Phases 5+).
 *
 * Les widgets UI lisent cet état au moment du rendu pour gérer hover, clic et
 * navigation clavier sans dépendre directement d'AWT.
 *
 * Le réflexe « consumeClick() / consumeKey() » sert à éviter qu'un même clic
 * soit traité par plusieurs widgets superposés.
 */
public class UiInputState {

    public int mouseX = -1;
    public int mouseY = -1;

    /** true sur la frame où un clic gauche vient d'être déclenché. */
    public boolean mouseClicked = false;

    /** true tant que le bouton gauche est maintenu. */
    public boolean mousePressed = false;

    private final Set<Integer> keysPressedThisFrame = new HashSet<>();
    private final Set<Integer> keysHeld             = new HashSet<>();

    public void onMouseMove(int x, int y) {
        mouseX = x;
        mouseY = y;
    }

    public void onMousePressed() {
        mousePressed = true;
        mouseClicked = true;
    }

    public void onMouseReleased() {
        mousePressed = false;
    }

    public void onKeyPressed(int keyCode) {
        if (!keysHeld.contains(keyCode)) {
            keysPressedThisFrame.add(keyCode);
        }
        keysHeld.add(keyCode);
    }

    public void onKeyReleased(int keyCode) {
        keysHeld.remove(keyCode);
    }

    public boolean wasKeyTyped(int keyCode) {
        return keysPressedThisFrame.contains(keyCode);
    }

    public boolean isKeyHeld(int keyCode) {
        return keysHeld.contains(keyCode);
    }

    /** Consomme le clic de la frame (les widgets suivants ne le reverront pas). */
    public void consumeClick() {
        mouseClicked = false;
    }

    /** Consomme une touche tapée (les widgets suivants ne la reverront pas). */
    public void consumeKey(int keyCode) {
        keysPressedThisFrame.remove(keyCode);
    }

    /** À appeler en fin de frame pour réinitialiser les événements ponctuels. */
    public void endFrame() {
        mouseClicked = false;
        keysPressedThisFrame.clear();
    }

    /** Helper d'inclusion point-dans-rectangle. */
    public boolean isMouseInside(float x, float y, float w, float h) {
        return mouseX >= x && mouseX < x + w
            && mouseY >= y && mouseY < y + h;
    }

    /** Code touche m (touche pour ouvrir/fermer le menu in-game). */
    public static final int KEY_M = KeyEvent.VK_M;
}
