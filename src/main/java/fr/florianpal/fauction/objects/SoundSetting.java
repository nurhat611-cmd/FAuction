package fr.florianpal.fauction.objects;

/**
 * One configurable sound of config.yml : what to play, how loud, at which pitch.
 * <p>
 * The name is either a vanilla sound constant ({@code ENTITY_ITEM_PICKUP},
 * {@code UI_BUTTON_CLICK}, ...) or any custom sound event a resource pack provides
 * ({@code custom.cashregister}, {@code mypack:ui.click}) — telling them apart is the job of
 * {@code SoundManager}.
 */
public record SoundSetting(String name, double volume, double pitch) {
}
