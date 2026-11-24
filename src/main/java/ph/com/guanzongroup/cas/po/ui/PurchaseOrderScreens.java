package ph.com.guanzongroup.cas.po.ui;

import java.util.Arrays;
import java.util.List;
import org.guanzon.appdriver.agent.screen.ScreenEntry;
import org.guanzon.appdriver.agent.screen.ScreenProvider;

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
public class PurchaseOrderScreens implements ScreenProvider {

    private static final String VIEWS = "/ph/com/guanzongroup/cas/po/ui/";
    private static final String INTEGSYS = "ph.com.guanzongroup.integsys.views.";

    @Override
    public String getModule() {
        return "cas-po";
    }

    @Override
    public List<ScreenEntry> getScreens() {
        return Arrays.asList(
                ScreenEntry.of(PurchaseOrderGeneral_EntryController.class,
                        VIEWS + "PurchaseOrderGeneral_Entry.fxml",
                        INTEGSYS + "PurchaseOrder_EntryController"),
                ScreenEntry.of(PurchaseOrderGeneral_ConfirmationController.class,
                        VIEWS + "PurchaseOrderGeneral_Confirmation.fxml",
                        INTEGSYS + "PurchaseOrder_ConfirmationController"),
                ScreenEntry.of(PurchaseOrderGeneral_HistoryController.class,
                        VIEWS + "PurchaseOrderGeneral_History.fxml",
                        INTEGSYS + "PurchaseOrder_HistoryController"));
    }
}
