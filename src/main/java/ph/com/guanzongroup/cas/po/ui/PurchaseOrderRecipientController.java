package ph.com.guanzongroup.cas.po.ui;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.guanzon.appdriver.agent.ShowMessageFX;
import org.guanzon.appdriver.base.GRiderCAS;
import org.guanzon.appdriver.base.MiscUtil;
import org.guanzon.appdriver.constant.EditMode;
import org.guanzon.appdriver.constant.RecordStatus;
import org.guanzon.cas.parameter.model.Model_Supplier_Email;
import org.json.simple.JSONObject;
import ph.com.guanzongroup.cas.po.controller.PurchaseOrderMP;
import ph.com.guanzongroup.cas.po.status.PurchaseOrderStaticData;

/**
 * Purchase Order Recipients (PurchaseOrderRecipient.fxml): chooses who receives
 * the email of a saved purchase order.
 *
 * <p>
 * Lists the supplier's email addresses with the recipient type (To, CC, BCC)
 * stored in Supplier_Email; the type is display only. Nothing is saved to
 * Send_Mail_Master here: that happens when the PO is confirmed. Okay closes the
 * screen. New (a pop-up,
 * see {@link PurchaseOrderRecipientEmailController}), Activate and Deactivate
 * change the supplier's addresses right away. They are enabled only while the
 * PO is being added or updated.</p>
 *
 * <p>
 * After loading the FXML, call {@link #setGRider(GRiderCAS)} and
 * {@link #setPurchaseOrder(PurchaseOrderMP)}, then {@link #loadForm()}.</p>
 */
public class PurchaseOrderRecipientController implements Initializable {

    private static final Logger LOG = Logger.getLogger(PurchaseOrderRecipientController.class.getName());
    private static final String FORM_NAME = PurchaseOrderStaticData.FORM_NAME_RECIPIENTS;


    @FXML
    private Button btnCLoseModal, btnOk;
    @FXML
    private Button btnNewEmail, btnActivate, btnDeactivate;
    @FXML
    private TextField tfTransactionNo;
    /** Supplier name. */
    @FXML
    private TextField tfCustomerName;
    /** PO transaction date. */
    @FXML
    private DatePicker dpInquiryDate;
    @FXML
    private TableView<RecipientRow> tblFollowUp;
    /** No, Email, Contact Person, Status. */
    @FXML
    private TableColumn<RecipientRow, String> tblRowFUNo, tblRowFUDate, tblRowFUNextDate, tblRowFUStatus;
    /** Recipient Type (display only). */
    @FXML
    private TableColumn<RecipientRow, String> tblRowFUNextDate1;

    private final ObservableList<RecipientRow> paRows = FXCollections.observableArrayList();
    private GRiderCAS poApp;
    private PurchaseOrderMP poMP;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        tfTransactionNo.setEditable(false);
        tfCustomerName.setEditable(false);

        initTable();

        btnOk.setOnAction(event -> close());
        btnCLoseModal.setOnAction(event -> close());
        btnNewEmail.setOnAction(event -> newEmail());
        btnActivate.setOnAction(event -> setStatus(true));
        btnDeactivate.setOnAction(event -> setStatus(false));

        initTooltips();
        initStatusButtons();
    }

    private void initTooltips() {
        btnNewEmail.setTooltip(new Tooltip("Add Email"));
        btnActivate.setTooltip(new Tooltip("Activate the selected email"));
        btnDeactivate.setTooltip(new Tooltip("Deactivate the selected email"));
    }

    public void setGRider(GRiderCAS foValue) {
        poApp = foValue;
    }

    /** The loaded (saved) purchase order. */
    public void setPurchaseOrder(PurchaseOrderMP foPO) {
        poMP = foPO;
    }

    /** Shows the recipients. Call after the setters. */
    public void loadForm() {
        if (poApp == null || poMP == null) {
            ShowMessageFX.Warning("The application driver or purchase order is not set.", FORM_NAME, null);
            return;
        }
        loadRecord();
    }

    private void initTable() {
        tblRowFUNo.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().psNumber));
        tblRowFUDate.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().psAddress));
        tblRowFUNextDate.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().psContact));
        tblRowFUStatus.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().pbActive ? "Active" : "Inactive"));
        tblRowFUNextDate1.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().psType));

        for (TableColumn<RecipientRow, String> loColumn : Arrays.asList(
                tblRowFUNo, tblRowFUDate, tblRowFUNextDate, tblRowFUNextDate1, tblRowFUStatus)) {
            loColumn.setSortable(false);
        }
        tblRowFUNo.setStyle("-fx-alignment: CENTER;");
        tblRowFUNextDate1.setStyle("-fx-alignment: CENTER;");
        tblRowFUStatus.setStyle("-fx-alignment: CENTER;");

        tblFollowUp.setItems(paRows);
        tblFollowUp.setPlaceholder(new Label("NO RECORD TO LOAD"));
        tblFollowUp.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldValue, newValue) -> initStatusButtons());
    }

    /** Loads the header and the supplier emails. */
    private void loadRecord() {
        try {
            tfTransactionNo.setText(poMP.Master().getTransactionNo());
            String lsSupplier = poMP.Master().Supplier().getCompanyName();
            tfCustomerName.setText(lsSupplier == null ? "" : lsSupplier);
            Date ldDate = poMP.Master().getTransactionDate();
            dpInquiryDate.setValue(ldDate == null ? null : new java.sql.Date(ldDate.getTime()).toLocalDate());

            paRows.clear();
            for (Model_Supplier_Email loEmail : poMP.getSupplierEmails()) {
                String lsContact = loEmail.getContactPerson();
                paRows.add(new RecipientRow(String.valueOf(paRows.size() + 1),
                        loEmail.getEmailAddress().trim(),
                        lsContact == null ? "" : lsContact,
                        RecordStatus.ACTIVE.equals(loEmail.getRecordStatus()),
                        typeLabel(loEmail.getRecipientType())));
            }

        } catch (Exception ex) {
            LOG.log(Level.SEVERE, MiscUtil.getException(ex), ex);
            paRows.clear();
            ShowMessageFX.Warning("Unable to load the recipients.\n" + MiscUtil.getException(ex), FORM_NAME, null);
        }
        initStatusButtons();
    }

    private void newEmail() {
//        if (checkMaintain()) {
        openEmailModal();
//        }
    }

    /** Opens the new email pop-up and shows the added address after it closes. */
    private void openEmailModal() {
        try {
            URL loLocation = getClass().getResource(PurchaseOrderStaticData.RECIPIENT_EMAIL_FXML);
            if (loLocation == null) {
                throw new IOException("Screen not found: " + PurchaseOrderStaticData.RECIPIENT_EMAIL_FXML);
            }
            FXMLLoader loader = new FXMLLoader(loLocation);
            Parent root = loader.load();

            // Pass the data to the modal controller
            PurchaseOrderRecipientEmailController modalController = loader.getController();
            modalController.setGRider(poApp);
            modalController.setPurchaseOrder(poMP);
            modalController.loadForm();   // must be AFTER the setters

            Stage modalStage = new Stage();
            modalStage.setTitle("New Supplier Email");
            modalStage.initStyle(StageStyle.UNDECORATED);
            modalStage.initModality(Modality.APPLICATION_MODAL);
            modalStage.initOwner(tblFollowUp.getScene().getWindow());

            // make the window movable
            final double[] xOffset = new double[1];
            final double[] yOffset = new double[1];

            root.setOnMousePressed(event -> {
                xOffset[0] = event.getSceneX();
                yOffset[0] = event.getSceneY();
            });

            root.setOnMouseDragged(event -> {
                modalStage.setX(event.getScreenX() - xOffset[0]);
                modalStage.setY(event.getScreenY() - yOffset[0]);
            });

            modalStage.setScene(new Scene(root));
            modalStage.setResizable(false);
            modalStage.showAndWait();

            if (modalController.isAdded()) {
                loadRecord();
            }
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Failed to load New Supplier Email Modal", e);
            ShowMessageFX.Error("Unable to open the new email window.", FORM_NAME, null);
        }
    }

    /** Activates or deactivates the selected address. */
    private void setStatus(boolean fbActive) {
        RecipientRow loRow = tblFollowUp.getSelectionModel().getSelectedItem();
        if (loRow == null ) {
            return;
        }
        String lsQuestion = "Are you sure you want to " + (fbActive ? "activate " : "deactivate ")
                + loRow.psAddress + "?";
        if (!ShowMessageFX.YesNo(lsQuestion, FORM_NAME, null)) {
            return;
        }
        try {
            JSONObject loJSON = poMP.setSupplierEmailStatus(loRow.psAddress, fbActive);
            if (!isSuccess(loJSON)) {
                ShowMessageFX.Warning(String.valueOf(loJSON.get("message")), FORM_NAME, null);
                return;
            }
            loadRecord();
            ShowMessageFX.Information(String.valueOf(loJSON.get("message")), FORM_NAME, null);
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, MiscUtil.getException(ex), ex);
            ShowMessageFX.Warning(MiscUtil.getException(ex), FORM_NAME, null);
        }
    }

    private boolean checkMaintain() {
        if (poMP != null && poMP.canMaintainSupplierEmails()) {
            return true;
        }
        ShowMessageFX.Warning("Only a system administrator can maintain supplier email addresses.", FORM_NAME, null);
        return false;
    }

    /**
     * New, Activate and Deactivate work only in Add New or Update. Activate is
     * for an inactive row, Deactivate for an active one.
     */
    private void initStatusButtons() {
        boolean lbEntry = isEntryMode();
        RecipientRow loRow = tblFollowUp.getSelectionModel().getSelectedItem();
        btnNewEmail.setDisable(!lbEntry);
        btnActivate.setDisable(!lbEntry || loRow == null || loRow.pbActive);
        btnDeactivate.setDisable(!lbEntry || loRow == null || !loRow.pbActive);
    }

    private boolean isEntryMode() {
        return poMP != null
                && (poMP.getEditMode() == EditMode.ADDNEW || poMP.getEditMode() == EditMode.UPDATE);
    }

    private void close() {
        ((Stage) btnOk.getScene().getWindow()).close();
    }

    /** Label of a Supplier_Email.cRecpient code; "To" when empty or unknown. */
    private static String typeLabel(String fsCode) {
        if (PurchaseOrderMP.RECIPIENT_CC.equals(fsCode)) {
            return PurchaseOrderStaticData.RECIPIENT_TYPE_CC;
        }
        return PurchaseOrderMP.RECIPIENT_BCC.equals(fsCode) ? PurchaseOrderStaticData.RECIPIENT_TYPE_BCC : PurchaseOrderStaticData.RECIPIENT_TYPE_TO;
    }

    static String typeCode(String fsLabel) {
        if (PurchaseOrderStaticData.RECIPIENT_TYPE_CC.equals(fsLabel)) {
            return PurchaseOrderMP.RECIPIENT_CC;
        }
        return PurchaseOrderStaticData.RECIPIENT_TYPE_BCC.equals(fsLabel) ? PurchaseOrderMP.RECIPIENT_BCC : PurchaseOrderMP.RECIPIENT_TO;
    }

    private static boolean isSuccess(JSONObject foJSON) {
        return foJSON != null && "success".equals(foJSON.get("result"));
    }

    /** One supplier email in the table. */
    private static final class RecipientRow {

        private final String psNumber;
        private final String psAddress;
        private final String psContact;
        private final boolean pbActive;
        /** Type stored in Supplier_Email. */
        private final String psType;

        private RecipientRow(String fsNumber, String fsAddress, String fsContact, boolean fbActive,
                             String fsType) {
            psNumber = fsNumber;
            psAddress = fsAddress;
            psContact = fsContact;
            pbActive = fbActive;
            psType = fsType;
        }
    }
}