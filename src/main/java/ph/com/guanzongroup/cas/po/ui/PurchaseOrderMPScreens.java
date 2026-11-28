package ph.com.guanzongroup.cas.po.ui;

import org.guanzon.appdriver.agent.screen.ScreenEntry;
import org.guanzon.appdriver.agent.screen.ScreenProvider;

import java.util.Arrays;
import java.util.List;

/**
 * The screens cas-po offers to cas-app.
 *
 * <p>
 * Registered in
 * {@code META-INF/services/org.guanzon.appdriver.agent.screen.ScreenProvider}.
 * The menu keys are the integsys controllers the existing menu rows
 * (xxxSysMenuOthers.sObjectNm) point to, so the same menu opens the old
 * screens in integsys and the new ones in cas-app; the menu tables do not
 * change.</p>
 *
 * <p>
 * To move another menu item to cas-po, add its line here.</p>
 */
public class PurchaseOrderMPScreens implements ScreenProvider {

    private static final String VIEWS = "/ph/com/guanzongroup/cas/po/ui/";
    private static final String INTEGSYS = "ph.com.guanzongroup.integsys.views.";

    @Override
    public String getModule() {
        return "cas-po";
    }

    @Override
    public List<ScreenEntry> getScreens() {
        return Arrays.asList(
                ScreenEntry.of(PurchaseOrderMP_EntryController.class,
                        VIEWS + "PurchaseOrderMP_Entry.fxml",
                        INTEGSYS + "PurchaseOrder_EntryController"),
                ScreenEntry.of(PurchaseOrderMP_LandingController.class,
                        VIEWS + "PurchaseOrderMP_Landing.fxml",
                        INTEGSYS + "PurchaseOrder_LandingController"),
                ScreenEntry.of(PurchaseOrderMP_EntryController.class,
                        VIEWS + "PurchaseOrderMP_History.fxml",
                        INTEGSYS + "PurchaseOrder_HistoryController"));
    }
}
