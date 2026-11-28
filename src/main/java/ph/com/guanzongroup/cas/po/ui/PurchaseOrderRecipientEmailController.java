package ph.com.guanzongroup.cas.po.ui;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.guanzon.appdriver.agent.ShowMessageFX;
import org.guanzon.appdriver.base.GRiderCAS;
import org.guanzon.appdriver.base.MiscUtil;
import org.json.simple.JSONObject;
import ph.com.guanzongroup.cas.po.controller.PurchaseOrderMP;
import ph.com.guanzongroup.cas.po.status.PurchaseOrderStaticData;

/**
 * New Supplier Email (PurchaseOrderRecipientEmail.fxml): a pop-up that adds an
 * email address to the supplier of the loaded purchase order. The new address
 * is always Active.
 *
 * <p>
 * After loading the FXML, call {@link #setGRider(GRiderCAS)} and
 * {@link #setPurchaseOrder(PurchaseOrderMP)}, then {@link #loadForm()}. After
 * the window closes, {@link #isAdded()} tells whether an address was saved.</p>
 */
public class PurchaseOrderRecipientEmailController implements Initializable {

    private static final Logger LOG = Logger.getLogger(PurchaseOrderRecipientEmailController.class.getName());
    private static final String FORM_NAME = PurchaseOrderStaticData.FORM_NAME_NEW_SUPPLIER_EMAIL;

    @FXML
    private Button btnAddEmail, btnCancelEmail, btnCloseModal;
    @FXML
    private TextField tfSupplier, tfNewEmail, tfNewContact;
    @FXML
    private ComboBox<String> cmbNewType;

    private GRiderCAS poApp;
    private PurchaseOrderMP poMP;
    /** The saved address and its type; null until an address is added. */
    private String psAddedEmail;
    private String psAddedType;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        cmbNewType.setItems(PurchaseOrderStaticData.RECIPIENT_TYPES);
        cmbNewType.getSelectionModel().select(PurchaseOrderStaticData.RECIPIENT_TYPE_TO);

        btnAddEmail.setOnAction(event -> addEmail());
        btnCancelEmail.setOnAction(event -> close());
        btnCloseModal.setOnAction(event -> close());
    }

    public void setGRider(GRiderCAS foValue) {
        poApp = foValue;
    }

    /** The loaded (saved) purchase order. */
    public void setPurchaseOrder(PurchaseOrderMP foPO) {
        poMP = foPO;
    }

    /** Shows the supplier. Call after the setters. */
    public void loadForm() {
        if (poApp == null || poMP == null) {
            btnAddEmail.setDisable(true);
            ShowMessageFX.Warning("The application driver or purchase order is not set.", FORM_NAME, null);
            return;
        }
        try {
            String lsSupplier = poMP.Master().Supplier().getCompanyName();
            tfSupplier.setText(lsSupplier == null ? "" : lsSupplier);
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, MiscUtil.getException(ex), ex);
        }
        Platform.runLater(() -> tfNewEmail.requestFocus());
    }

    public boolean isAdded() {
        return psAddedEmail != null;
    }

    public String getAddedEmail() {
        return psAddedEmail;
    }

    /** To, CC or BCC. */
    public String getAddedType() {
        return psAddedType;
    }

    private void addEmail() {
        String lsAddress = tfNewEmail.getText().trim();
        String lsType = cmbNewType.getValue();
        try {
            JSONObject loJSON = poMP.addSupplierEmail(lsAddress, tfNewContact.getText(),
                    PurchaseOrderRecipientController.typeCode(lsType));
            if (!"success".equals(loJSON.get("result"))) {
                ShowMessageFX.Warning(String.valueOf(loJSON.get("message")), FORM_NAME, null);
                return;
            }
            psAddedEmail = lsAddress;
            psAddedType = lsType;
            ShowMessageFX.Information(String.valueOf(loJSON.get("message")), FORM_NAME, null);
            closeWindow();
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, MiscUtil.getException(ex), ex);
            ShowMessageFX.Warning(MiscUtil.getException(ex), FORM_NAME, null);
        }
    }

    private void close() {
        boolean lbTyped = !tfNewEmail.getText().trim().isEmpty() || !tfNewContact.getText().trim().isEmpty();
        if (lbTyped && !ShowMessageFX.YesNo("Discard this new email?", FORM_NAME, null)) {
            return;
        }
        closeWindow();
    }

    private void closeWindow() {
        ((Stage) btnAddEmail.getScene().getWindow()).close();
    }
}