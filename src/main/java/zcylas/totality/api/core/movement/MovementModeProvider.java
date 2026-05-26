package zcylas.totality.api.core.movement;

import java.util.Set;

/**
 * Interface implemented by passive Ability classes that unlock movement modes.
 *
 * Example:
 *   public class ViltrumitePhysiology extends Ability implements MovementModeProvider {
 *       public Set<MovementMode> getGrantedModes() {
 *           return Set.of(MovementMode.FLIGHT, MovementMode.POWER_SPRINT, MovementMode.SUPER_LEAP);
 *       }
 *   }
 *
 * The movement system checks all unlocked PASSIVE abilities for this interface
 * at runtime — no registration needed beyond implementing the interface.
 */
public interface MovementModeProvider {
    Set<MovementMode> getGrantedModes();
}