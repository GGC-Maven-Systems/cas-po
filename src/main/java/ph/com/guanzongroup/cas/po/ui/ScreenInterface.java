package ph.com.guanzongroup.cas.po.ui;

/**
 * What a screen receives before it is shown.
 *
 * <p>
 * Now the shared contract of appdriver-agent
 * ({@link org.guanzon.appdriver.agent.screen.ScreenInterface}), so cas-app can
 * open the PO screens. Kept so the screens' existing
 * {@code implements ScreenInterface} still compiles; new screens can implement
 * the agent interface directly.</p>
 */
public interface ScreenInterface extends org.guanzon.appdriver.agent.screen.ScreenInterface {
}
