/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/javafx/FXMLController.java to edit this template
 */
package ph.com.guanzongroup.cas.po.ui;

import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanPropertyBase;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.guanzon.appdriver.agent.ShowDialogFX;
import org.guanzon.appdriver.agent.ShowMessageFX;
import org.guanzon.appdriver.base.*;
import org.guanzon.appdriver.constant.DocumentType;
import org.guanzon.appdriver.constant.EditMode;
import org.guanzon.appdriver.constant.RecordStatus;
import org.guanzon.appdriver.constant.UserRight;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import ph.com.guanzongroup.cas.po.controller.PurchaseOrderMP;
import ph.com.guanzongroup.cas.po.model.Model_PO_Detail;
import ph.com.guanzongroup.cas.po.services.PurchaseOrderControllers;
import ph.com.guanzongroup.cas.po.status.PurchaseOrderStaticData;
import ph.com.guanzongroup.cas.po.status.PurchaseOrderStatus;
import ph.com.guanzongroup.cas.po.ui.model.ModelPOAttachment;
import ph.com.guanzongroup.cas.po.ui.model.ModelPurchaseOrder;
import ph.com.guanzongroup.cas.po.ui.model.ModelPurchaseOrderDetail;
import ph.com.guanzongroup.cas.po.ui.utility.*;
import ph.com.guanzongroup.cas.po.utility.Suggestion;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static javafx.scene.input.KeyCode.ENTER;
import static javafx.scene.input.KeyCode.TAB;

/**
 * Purchase Order entry screen for Mobile Phone Inventory,
 * using the new Purchase Order MP controller.
 *
 * <p>
 * A copy of integsys' PurchaseOrder_EntryController (which stays on the old
 * controller), rewired to ph.com.guanzongroup.cas.po. Covers encoding
 * up to confirmation: header, stock requests / quotations, items (add, edit,
 * remove with the Delete key or the row menu), discounts, advance payment,
 * attachments, save, then confirm (General Inventory auto-approves after
 * confirming).</p>
 *
 * <p>
 * Runs using the MP Purchase Order application launcher.
 */
public class PurchaseOrderMP_PreviewController implements Initializable, ScreenInterface {

    private GRiderCAS poApp;
    private PurchaseOrderControllers poPurchasingController;
    private PurchaseOrderMP poMP;

    private String psFormName = PurchaseOrderStaticData.FORM_NAME_PURCHASE_ORDER;
    private LogWrapper logWrapper;
    private JSONObject poJSON;

    private int pnEditMode;
    private int pnTblMainRow = -1;
    private int pnTblDetailRow = -1;
    private int pnTblMain_Page = 50;

    private String prevSupplier = "";
    private String psIndustryID = "";
    private String psCompanyID = "";
    private String psCategoryID = "";
    private String psOldDate = "";
    String allowedDepartment = System.getProperty("allowed.department");
    /** What the Close button does (e.g. close the tab); default: close the window. */
    private Runnable poOnClose;
    private ObservableList<ModelPurchaseOrder> main_data = FXCollections.observableArrayList();
    private ObservableList<ModelPurchaseOrderDetail> detail_data = FXCollections.observableArrayList();

    private TextField activeField;
    @FXML
    private AnchorPane AnchorMaster, AnchorDetails, AnchorMain, apBrowse, apButton,apMaster;
    @FXML
    private HBox hbButtons;
    @FXML
    private Button btnConfirm, btnApprove, btnVoid, btnCancelTransaction;
    @FXML
    private Button btnBrowse, btnNew, btnUpdate, btnSearch, btnSave, btnCancel,
            btnPrint, btnRetrieve, btnTransHistory, btnClose, btnRecipient;
    @FXML
    private TextField tfTransactionNo, tfSupplier, tfDestination, tfReferenceNo,
            tfTerm, tfDiscountRate, tfDiscountAmount, tfAdvancePRate, tfAdvancePAmount, tfTotalAmount, tfNetAmount;
    @FXML
    private Label lblTransactionStatus, lblSource;
    @FXML
    private CheckBox chkbAdvancePayment;
    @FXML
    private DatePicker dpTransactionDate, dpExpectedDlvrDate;
    @FXML
    private TextField tfBarcode, tfDescription, tfBrand, tfModel, tfColor, tfVariant, tfInventoryType,
            tfClass, tfAMC, tfROQ, tfRO, tfBO, tfQOH, tfCost, tfRequestQuantity, tfOrderQuantity,tfAddDesc;
    @FXML
    private TextArea taRemarks;
    @FXML
    private TableView<ModelPurchaseOrderDetail> tblVwOrderDetails;
    @FXML
    private TableColumn<ModelPurchaseOrderDetail, String> tblRowNoDetail, tblOrderNoDetail, tblBarcodeDetail, tblDescriptionDetail, tblAdditionalDescDetail,
            tblCostDetail, tblROQDetail, tblRequestQuantityDetail, tblOrderQuantityDetail, tblTotalAmountDetail;
    @FXML
    private TableView<ModelPurchaseOrder> tblVwStockRequest;

    @FXML
    private TableColumn<ModelPurchaseOrder, String> tblRowNo, tblBranchName, tblDate, tblReferenceNo, tblNoOfItems;
    @FXML
    private AnchorPane apTableStockRequestLoading, apTableDetailLoading;
    @FXML
    private ProgressIndicator piTableStockRequestLoading, piTableDetailLoading;
    @FXML
    private Pagination pagination;
    @FXML
    private TableView tblAttachments;
    @FXML
    private TableColumn tblRowNoAttachment,tblFileNameAttachment;

    @FXML
    private ImageView imageView;

    @FXML
    private ComboBox cmbAttachmentType;

    @FXML
    private ComboBox<String> cmbOrderType;
    /** True while the PO's order type is being shown, so it is not set back to the PO. */
    private boolean pbShowingOrderType;

    @FXML
    private TextField tfAttachmentNo;
    @FXML
    private AnchorPane apAttachments;

    private FileChooser fileChooser;
    private int pnAttachment;
    private double mouseAnchorX;
    private double mouseAnchorY;
    private double scaleFactor = 1.0;
    private int currentIndex = 0;
    double ldstackPaneWidth = 0;
    double ldstackPaneHeight = 0;
    @FXML
    private StackPane stackPane1;
    private ObservableList<ModelPOAttachment> attachment_data = FXCollections.observableArrayList();
    ObservableList<String> documentType = ModelPOAttachment.documentType;
    @FXML
    private Button  btnAddAttachment, btnRemoveAttachment, btnArrowLeft, btnArrowRight;

    @Override
    public void setGRider(GRiderCAS foValue) {
        poApp = foValue;
    }

    @Override
    public void setIndustryID(String fsValue) {
        psIndustryID = fsValue;
    }

    @Override
    public void setCompanyID(String fsValue) {
        psCompanyID = fsValue;
    }

    @Override
    public void setCategoryID(String fsValue) {
        psCategoryID = fsValue;
    }

    /** What the Close button does (e.g. remove the tab); default: close the window. */
    @Override
    public void setOnClose(Runnable foOnClose) {
        poOnClose = foOnClose;
    }

    /**
     * Hands over the purchase order opened by the landing screen
     * (OpenTransaction already done). Call this BEFORE {@link #loadForm()}.
     */
    public void setPurchaseOrder(PurchaseOrderMP foPurchaseOrder) {
        poMP = foPurchaseOrder;
    }

    /**
     * Returns the initialized MP Purchase Order controller.
     *
     * @return PurchaseOrderMP instance
     */
    private PurchaseOrderMP po() {
        return poMP;
    }

    /**
     * Initializes the MP Purchase Order controller through the service.
     *
     * @return true if initialization is successful; otherwise false
     */
    private boolean initPurchaseOrder() {
        try {
            // Initialize the Purchase Order service
            poPurchasingController = new PurchaseOrderControllers(
                    poApp,
                    logWrapper
            );

            // Request the appropriate controller from the service
            Object loPO = poPurchasingController.PurchaseOrder(psIndustryID);

            if (loPO == null) {
                throw new IllegalStateException(
                        "Unable to create Purchase Order controller."
                );
            }

            // Validate the returned implementation
            if (!(loPO instanceof PurchaseOrderMP)) {
                throw new IllegalStateException(
                        "Invalid Purchase Order controller for industry: "
                                + psIndustryID
                );
            }

            // Retain the MP controller instance
            poMP = (PurchaseOrderMP) loPO;

            // Set allowed transaction statuses
            poMP.setTransactionStatus(
                    PurchaseOrderStatus.OPEN + PurchaseOrderStatus.RETURNED
            );

            // Initialize transaction
            poJSON = poMP.InitTransaction();

            if (poJSON == null || !"success".equals(poJSON.get("result"))) {
                String lsMessage = poJSON == null
                        ? "Unable to initialize Purchase Order."
                        : String.valueOf(poJSON.get("message"));

                Logger.getLogger(getClass().getName())
                        .severe(lsMessage);

                return false;
            }

            // Set Purchase Order master information
            poMP.Master().setIndustryID(psIndustryID);
            poMP.Master().setCategoryCode(psCategoryID);
            if (psCompanyID == null || psCompanyID.trim().isEmpty()) {
                System.out.println("Company ID is null or empty. Using default company ID: " + poApp.getCompnyId());
                System.out.println("Company ID from system property: " + System.getProperty("user.selected.company"));
                poMP.Master().setCompanyID(poApp.getCompnyId());
            } else {
                poMP.Master().setCompanyID(psCompanyID);
            }

            return true;

        } catch (Exception ex) {
            Logger.getLogger(getClass().getName())
                    .log(Level.SEVERE,
                            "Purchase Order initialization failed.",
                            ex);

            return false;
        }
    }

    /**
     * Initializes the controller class. Only the controls are prepared here:
     * the purchase order is not known yet (the landing screen hands it over
     * after the FXML is loaded), so it is shown by {@link #loadForm()}.
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        try {
            tblVwOrderDetails.addEventFilter(
                    KeyEvent.KEY_PRESSED,
                    this::tableKeyEvents
            );
            initAll();

            //nothing is loaded yet: only Close until loadForm() sets the edit mode
            pnEditMode = EditMode.UNKNOWN;
            initButtons(pnEditMode);
            initFields(pnEditMode);
        } catch (Exception ex) {
            Logger.getLogger(getClass().getName())
                    .log(Level.SEVERE,
                            "Unable to initialize Purchase Order preview screen.",
                            ex);
        }
    }

    /** Shows the opened transaction read-only (double click on the landing). */
    public void loadForm() {
        loadForm(false);
    }

    /**
     * Shows the transaction that the landing screen opened with OpenTransaction.
     * Must be called AFTER {@link #setGRider(GRiderCAS)} and
     * {@link #setPurchaseOrder(PurchaseOrderMP)}.
     *
     * @param fbUpdate true to also switch the screen to edit mode (Update button of the landing)
     */
    public void loadForm(boolean fbUpdate) {
        if (po() == null) {
            ShowMessageFX.Warning("No purchase order was given to preview.", psFormName, null);
            return;
        }
        try {
            pnEditMode = po().getEditMode();   // READY after OpenTransaction
            pbFocusHeader = false;

            clearDetailFields();
            loadRecordSearch();                // company
            loadRecordMaster();                // header, totals, status

            //1st item row, as the Browse of the entry screen does
            pnTblDetailRow = po().getDetailCount() > 0 ? 0 : -1;
            loadTableDetail();                 // item table (selects row 0)
            loadRecordDetail();                // fills barcode, description, cost, qty, etc.
            loadTableDetailFromMain();         // attachments

            initButtons(pnEditMode);
            initFields(pnEditMode);

            if (fbUpdate) {
                btnUpdate.fire();              // same flow as Update of the entry screen: edit mode
            }
        } catch (Exception ex) {
            Logger.getLogger(getClass().getName())
                    .log(Level.SEVERE, "Unable to load the purchase order preview.", ex);
            ShowMessageFX.Error("Unable to load the purchase order preview.", psFormName, null);
        }
    }

    /**
     * After Save (and the confirm / print prompts): opens the saved transaction again
     * and shows it read-only, so the screen keeps open with the data as saved.
     */
    private void refreshSaved(String fsTransNo) {
        try {
            poJSON = po().OpenTransaction(fsTransNo);
            if (!"success".equals(poJSON.get("result"))) {
                ShowMessageFX.Warning(String.valueOf(poJSON.get("message")), psFormName, null);
                return;
            }
            loadForm(false);
        } catch (Exception ex) {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, MiscUtil.getException(ex), ex);
            ShowMessageFX.Error(MiscUtil.getException(ex), psFormName, null);
        }
    }

    private interface StatusCall {
        JSONObject run(String fsRemarks) throws Exception;
    }

    /**
     * Confirm / Approve / Void of the transaction on screen: asks, asks the remarks when
     * needed, runs the status change, then reloads the transaction so the screen (status and
     * buttons) shows the new state.
     */
    private void runStatusAction(String fsTitle, String fsQuestion, boolean fbRemarks, StatusCall foCall) {
        try {
            if (!ShowMessageFX.YesNo(null, psFormName, fsQuestion)) {
                return;
            }
            String lsRemarks = "";
            if (fbRemarks) {
                TextInputDialog loDialog = new TextInputDialog();
                loDialog.setTitle(psFormName);
                loDialog.setHeaderText(fsTitle + " Purchase Order");
                loDialog.setContentText("Remarks:");
                Optional<String> loRemarks = loDialog.showAndWait();
                if (!loRemarks.isPresent() || loRemarks.get().trim().isEmpty()) {
                    ShowMessageFX.Warning("Remarks are required to " + fsTitle.toLowerCase() + " a purchase order.", psFormName, null);
                    return;
                }
                lsRemarks = loRemarks.get().trim();
            }

            String lsTransNo = po().Master().getTransactionNo();
            po().setWithUI(true);
            poJSON = foCall.run(lsRemarks);
            if ("success".equals(poJSON.get("result"))) {
                ShowMessageFX.Information(String.valueOf(poJSON.get("message")), psFormName, null);
            } else {
                ShowMessageFX.Warning(String.valueOf(poJSON.get("message")), psFormName, null);
            }
            refreshSaved(lsTransNo);
        } catch (Exception ex) {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, MiscUtil.getException(ex), ex);
            ShowMessageFX.Error(MiscUtil.getException(ex), psFormName, null);
        }
    }

    /** Closes this screen (what the launcher set, else the window). */
    private void closeScreen() {
        if (poOnClose != null) {
            poOnClose.run();
        } else {
            ((Stage) AnchorMain.getScene().getWindow()).close();
        }
    }

    private void loadRecordSearch() {
        if (lblSource == null) {
            return; //not in the preview FXML
        }
        try {
            lblSource.setText(po().Master().Company().getCompanyName());
        } catch (GuanzonException | SQLException ex) {
            Logger.getLogger(PurchaseOrderMP_PreviewController.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void initAll() {
        initButtonsClickActions();
        initTextFieldFocus();
        initTextAreaFocus();
        initTextFieldKeyPressed();
        initAutoSuggest();
        initScreenKeys();
        initTextFieldsProperty();
        initCheckBoxActions();
        initDatePickerActions();
        initTextFieldPattern();
        initTableDetail();
        initDetailContextMenu();
        initAttachmentsGrid();
        initAttachmentPreviewPane();
        initStackPaneListener();
        initComboBoxes();
        tblVwOrderDetails.setOnMouseClicked(this::tblVwDetail_Clicked);
        tblAttachments.setOnMouseClicked(event -> {
            pnAttachment = tblAttachments.getSelectionModel().getSelectedIndex();
            if (pnAttachment >= 0) {
                scaleFactor = 1.0;
                loadRecordAttachment(true);
                resetImageBounds();
            }
        });
    }

    private int moveToNextRow(TableView<?> table, TablePosition<?, ?> focusedCell) {
        if (table.getItems().isEmpty()) {
            return -1; // No movement possible
        }
        int nextRow = (focusedCell.getRow() + 1) % table.getItems().size();
        table.getSelectionModel().select(nextRow);
        return nextRow;
    }

    private int moveToPreviousRow(TableView<?> table, TablePosition<?, ?> focusedCell) {
        if (table.getItems().isEmpty()) {
            return -1; // No movement possible
        }
        int previousRow = (focusedCell.getRow() - 1 + table.getItems().size()) % table.getItems().size();
        table.getSelectionModel().select(previousRow);
        return previousRow;
    }

    private void tableKeyEvents(KeyEvent event) {
        TableView<?> currentTable = (TableView<?>) event.getSource();
        TablePosition<?, ?> focusedCell = currentTable.getFocusModel().getFocusedCell();

        if (focusedCell != null && "tblVwOrderDetails".equals(currentTable.getId())) {
            switch (event.getCode()) {
                case TAB:
                case DOWN:
                    pnTblDetailRow = pnTblDetailRow;
                    if (pnEditMode != EditMode.ADDNEW || pnEditMode != EditMode.UPDATE) {
                        pnTblDetailRow = moveToNextRow(currentTable, focusedCell);
                    }
                    break;
                case UP:
                    pnTblDetailRow = pnTblDetailRow;
                    if (pnEditMode != EditMode.ADDNEW || pnEditMode != EditMode.UPDATE) {
                        pnTblDetailRow = moveToPreviousRow(currentTable, focusedCell);
                    }
                    break;
                case DELETE:
                    removeSelectedDetail();
                    event.consume();
                    return;
                default:
                    return;
            }
//            currentTable.getSelectionModel().select(pnTblDetailRow);
//            currentTable.getFocusModel().focus(pnTblDetailRow);
            loadRecordDetail();
            initDetailFocus();
            event.consume();
        }
    }

    private void loadRecordMaster() {
        try {
            tfTransactionNo.setText(po().Master().getTransactionNo());
            lblTransactionStatus.setText(statusText());
            loadRecordOrderType();
            dpTransactionDate.setValue(CustomCommonUtil.parseDateStringToLocalDate(SQLUtil.dateFormat(po().Master().getTransactionDate(), SQLUtil.FORMAT_SHORT_DATE)));
            tfSupplier.setText(po().Master().Supplier().getCompanyName() != null ? po().Master().Supplier().getCompanyName() : "");
            tfDestination.setText(po().Master().Branch().getBranchName() != null ? po().Master().Branch().getBranchName() : "");
            tfReferenceNo.setText(po().Master().getReference());
            tfTerm.setText(po().Master().Term().getDescription() != null ? po().Master().Term().getDescription() : "");
            taRemarks.setText(po().Master().getRemarks());
            dpExpectedDlvrDate.setValue(CustomCommonUtil.parseDateStringToLocalDate(SQLUtil.dateFormat(po().Master().getExpectedDate(), SQLUtil.FORMAT_SHORT_DATE)));
            tfDiscountRate.setText(CustomCommonUtil.setIntegerValueToDecimalFormat(po().Master().getDiscount().doubleValue()));
            tfDiscountAmount.setText(CustomCommonUtil.setIntegerValueToDecimalFormat(po().Master().getAdditionalDiscount(), true));
            chkbAdvancePayment.setSelected(po().Master().getWithAdvPaym() == true);
            tfAdvancePRate.setText(CustomCommonUtil.setIntegerValueToDecimalFormat(po().Master().getDownPaymentRatesPercentage().doubleValue()));
            tfAdvancePAmount.setText(CustomCommonUtil.setIntegerValueToDecimalFormat(po().Master().getDownPaymentRatesAmount(), true));
            tfTotalAmount.setText(CustomCommonUtil.setIntegerValueToDecimalFormat(po().Master().getTranTotal(), true));
            tfNetAmount.setText(CustomCommonUtil.setIntegerValueToDecimalFormat(po().Master().getNetTotal(), true));
        } catch (GuanzonException | SQLException ex) {
            Logger.getLogger(PurchaseOrderMP_PreviewController.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    /** Selects the PO's order type; the saved value is the index in the list. */
    private void loadRecordOrderType() {
        String lsOrderType = String.valueOf(po().Master().getValue("cOrdrType"));
        int lnIndex = Arrays.asList(PurchaseOrderStaticData.ORDER_TYPE_CODE).indexOf(lsOrderType);

        pbShowingOrderType = true;
        try {
            if (lnIndex >= 0) {
                cmbOrderType.getSelectionModel().select(lnIndex);
            } else {
                cmbOrderType.getSelectionModel().clearSelection();
            }
        } finally {
            pbShowingOrderType = false;
        }
    }

    private void loadRecordDetail() {
        try {
            if (pnTblDetailRow < 0 || pnTblDetailRow > po().getDetailCount() - 1) {
                return;
            }
            boolean lbShow = JFXUtil.isObjectEqualTo(pnEditMode, EditMode.UPDATE, EditMode.ADDNEW)
                    && JFXUtil.isObjectEqualTo(po().Detail(pnTblDetailRow).getSouceCode(), null, "");
            JFXUtil.setDisabled(!lbShow, tfCost);
            if (pnTblDetailRow >= 0) {
                String lsStockId = po().Detail(pnTblDetailRow).getStockID();
                if (lsStockId == null || lsStockId.isEmpty()) {
                    clearDetailFields(); //the empty row for typing: nothing to look up
                    return;
                }
                CustomCommonUtil.setDisable(PurchaseOrderStatus.SourceCode.POQUOTATION.equals(po().Detail(pnTblDetailRow).getSouceCode()),
                        tfRequestQuantity);

                tfBarcode.setText(po().Detail(pnTblDetailRow).Inventory().getBarCode() != null ? po().Detail(pnTblDetailRow).Inventory().getBarCode() : "");
                tfDescription.setText(po().Detail(pnTblDetailRow).Inventory().getDescription() != null ? po().Detail(pnTblDetailRow).Inventory().getDescription() : "");
                tfAddDesc.setText(po().Detail(pnTblDetailRow).getDescription() != null ? po().Detail(pnTblDetailRow).getDescription() : "");
                tfBrand.setText(po().Detail(pnTblDetailRow).Inventory().Brand().getDescription());
                tfModel.setText(po().Detail(pnTblDetailRow).Inventory().Model().getDescription());
                tfColor.setText(po().Detail(pnTblDetailRow).Inventory().Color().getDescription());
                tfVariant.setText(po().Detail(pnTblDetailRow).Inventory().Variant().getDescription());
                tfInventoryType.setText(po().Detail(pnTblDetailRow).Inventory().InventoryType().getDescription());
//                tfMeasure.setText(po().Detail(pnTblDetailRow).Inventory().Measure().getDescription());
                tfClass.setText(po().Detail(pnTblDetailRow).InventoryMaster().getInventoryClassification());
                tfAMC.setText(CustomCommonUtil.setIntegerValueToDecimalFormat(po().Detail(pnTblDetailRow).InventoryMaster().getAverageCost()));
                tfROQ.setText("0");

                double lnRO = 0, lnBO = 0, lnQOH = 0, lnRequestQuantity = 0;
                switch (po().Detail(pnTblDetailRow).getSouceCode()) {
                    case PurchaseOrderStatus.SourceCode.STOCKREQUEST:
                        lnRO = po().Detail(pnTblDetailRow).InvStockRequestDetail().getReceived();
                        lnBO = po().Detail(pnTblDetailRow).InvStockRequestDetail().getBackOrder();
                        lnQOH = po().Detail(pnTblDetailRow).InvStockRequestDetail().getQuantityOnHand();
                        lnRequestQuantity = po().Detail(pnTblDetailRow).InvStockRequestDetail().getApproved();
                        break;
                    case PurchaseOrderStatus.SourceCode.POQUOTATION:
                        lnRO = 0;
                        lnBO = 0;
                        lnQOH = 0;
                        lnRequestQuantity = po().Detail(pnTblDetailRow).POQuotationDetail().getQuantity();
                        break;
                }

                tfRO.setText(CustomCommonUtil.setDecimalValueToIntegerFormat(lnRO));
                tfBO.setText(CustomCommonUtil.setDecimalValueToIntegerFormat(lnBO));
                tfQOH.setText(CustomCommonUtil.setDecimalValueToIntegerFormat(lnQOH));
                tfCost.setText(CustomCommonUtil.setIntegerValueToDecimalFormat(po().Detail(pnTblDetailRow).getUnitPrice().doubleValue(), true));
                tfRequestQuantity.setText(CustomCommonUtil.setDecimalValueToIntegerFormat(lnRequestQuantity));
                tfOrderQuantity.setText(String.valueOf(po().Detail(pnTblDetailRow).getQuantity().intValue()));
            }
        } catch (GuanzonException | SQLException ex) {
            Logger.getLogger(PurchaseOrderMP_PreviewController.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void initButtonsClickActions() {
        List<Button> buttons = Arrays.asList(btnUpdate, btnSearch, btnSave, btnCancel, btnConfirm, btnApprove, btnVoid, btnCancelTransaction,
                btnPrint, btnTransHistory, btnClose, btnAddAttachment, btnRemoveAttachment, btnArrowLeft, btnArrowRight,
                btnRecipient);

        buttons.forEach(button -> button.setOnAction(this::handleButtonAction));
    }

    private void handleButtonAction(ActionEvent event) {
        try {
            String lsButton = ((Button) event.getSource()).getId();
            switch (lsButton) {
                case "btnBrowse":
                    poJSON = po().SearchTransaction("",
                            po().Master().getSupplierID(),
                            "","",1);
                    if (!"error".equals((String) poJSON.get("result"))) {
                        tblVwStockRequest.getSelectionModel().clearSelection(pnTblDetailRow);
                        loadRecordMaster();
                        pnEditMode = po().getEditMode();
                        loadTableDetailFromMain();

                        //select the 1st item row of the loaded transaction
                        pnTblDetailRow = po().getDetailCount() > 0 ? 0 : -1;
                        clearDetailFields();
                        loadTableDetail();      //reselectLastDetailRow() now selects row 0
                        loadRecordDetail();     //fills barcode, description, cost, qty, etc.

                        selectTheExistedDetailFromMainTable();
                        if (!tfCost.getText().isEmpty() && !tfSupplier.getText().isEmpty()) {
                            loadTableMain();
                        }
                    } else {
                        ShowMessageFX.Warning((String) poJSON.get("message"), "Search Information", null);
                    }
                    break;
                case "btnNew":
                    clearDetailFields();
                    clearMasterFields();
                    detail_data.clear();
                    attachment_data.clear();
                    po().resetattachment();
                    poJSON = po().NewTransaction();
                    if ("success".equals((String) poJSON.get("result"))) {
                        po().Master().setSupplierID(prevSupplier);
                        po().Master().setIndustryID(psIndustryID);
                        po().Master().setCategoryCode(psCategoryID);
                        if (psCompanyID == null || psCompanyID.trim().isEmpty()) {
                            po().Master().setCompanyID(poApp.getCompnyId());
                        } else {
                            po().Master().setCompanyID(psCompanyID);
                        }
                        po().Master().setInventoryTypeCode(po().getInventoryTypeCode());
                        loadRecordMaster();
                        pnTblDetailRow = 0;
                        pnEditMode = po().getEditMode();
                        //a new PO starts at Supplier, not at the item row
                        pbFocusHeader = true;
                        loadTableDetail();
                        loadTableDetailAndSelectedRow();
                        Platform.runLater(() -> tfSupplier.requestFocus());
                    } else {
                        ShowMessageFX.Warning((String) poJSON.get("message"), "Warning", null);
                    }
                    break;
                case "btnUpdate":
                    poJSON = po().UpdateTransaction();
                    if ("error".equals((String) poJSON.get("result"))) {
                        ShowMessageFX.Warning((String) poJSON.get("message"), "Warning", null);
                        break;
                    }
                    loadRecordMaster();
                    po().loadAttachments();
                    pnEditMode = po().getEditMode();

                    //select the 1st item row of the transaction being updated
                    pnTblDetailRow = po().getDetailCount() > 0 ? 0 : -1;
                    clearDetailFields();
                    loadTableDetail();      //reselectLastDetailRow() selects row 0
                    loadRecordDetail();     //fills barcode, description, cost, qty, etc.

                    selectTheExistedDetailFromMainTable();
                    if (!tfSupplier.getText().isEmpty()) {
                        loadTableMain();
                    }
                    break;
                case "btnSearch":
                    //the same search as Enter / F3 in the field
                    if (activeField != null) {
                        searchField(activeField.getId(), activeField.getText().trim());
                    }
                    break;
                case "btnSave":
                    if (!ShowMessageFX.YesNo(null, psFormName, "Are you sure you want to save?")) {
                        return;
                    }
                    LocalDate selectedLocalDate = dpTransactionDate.getValue();
                    if (pnEditMode == EditMode.UPDATE) {
                        if (!psOldDate.isEmpty()) {
                            if (!CustomCommonUtil.formatLocalDateToShortString(selectedLocalDate).equals(psOldDate) && tfReferenceNo.getText().isEmpty()) {
                                ShowMessageFX.Warning("A reference number is required for backdated transactions.", psFormName, null);
                                return;
                            }
                        }
                    }

                    prevSupplier = po().Master().getSupplierID();

                    // Validate Detail Count Before Backend Processing
                    int detailCount = po().getDetailCount();
                    boolean hasValidItem = false; // True if at least one valid item exists

                    if (detailCount == 0) {
                        ShowMessageFX.Warning("Your order is empty. Please add at least one item.", psFormName, null);
                        return;
                    }

                    for (int lnCntr = 0; lnCntr <= detailCount - 1; lnCntr++) {
                        int quantity = po().Detail(lnCntr).getQuantity().intValue();
                        String stockID = po().Detail(lnCntr).getStockID();

                        // If any stock ID is empty OR quantity is 0, show an error and prevent saving
                        if (detailCount == 1) {
                            if (stockID == null || stockID.trim().isEmpty() || quantity == 0) {
                                ShowMessageFX.Warning("Invalid item in order. Ensure all items have a valid Stock ID and quantity greater than 0.", psFormName, null);
                                return;
                            }
                        }

                        hasValidItem = true;
                    }

                    // If no valid items exist, prevent saving
                    if (!hasValidItem) {
                        ShowMessageFX.Warning("Your order must have at least one valid item with a Stock ID and quantity greater than 0.", psFormName, null);
                        return;
                    }

                    // Assign modification details for Update Mode
                    if (pnEditMode == EditMode.UPDATE) {
                        po().Master().setModifiedDate(poApp.getServerDate());
                        po().Master().setModifyingId(poApp.getUserID());
                    }

                    // Assign modification date to all details
                    for (int lnCntr = 0; lnCntr < detailCount; lnCntr++) {
                        po().Detail(lnCntr).setOldPrice(po().Detail(lnCntr).Inventory().getCost());
                        po().Detail(lnCntr).setModifiedDate(poApp.getServerDate());
                    }

                    // Save Transaction
                    poJSON = po().isDetailHasZeroQty();
                    if (!"success".equals((String) poJSON.get("result"))) {
                        if ("true".equals((String) poJSON.get("warning"))) {
                            ShowMessageFX.Warning((String) poJSON.get("message"), psFormName, null);
                            pnTblDetailRow = (int) poJSON.get("tableRow");
                            loadTableDetail();
                            loadRecordDetail();
                            initDetailFocus();
                            return;
                        } else {
                            if (!ShowMessageFX.YesNo((String) poJSON.get("message"), psFormName, null)) {
                                pnTblDetailRow = (int) poJSON.get("tableRow");
                                loadTableDetail();
                                loadRecordDetail();
                                initDetailFocus();
                                return;
                            }
                        }
                    }
                    poJSON = po().SaveTransaction();
                    if (!"success".equals((String) poJSON.get("result"))) {
                        ShowMessageFX.Warning((String) poJSON.get("message"), psFormName, null);
                        loadTableDetail();
                        return;
                    }
                    ShowMessageFX.Information((String) poJSON.get("message"), psFormName, null);
                    final String lsSavedTransNo = po().Master().getTransactionNo();
                    poJSON = po().OpenTransaction(lsSavedTransNo);
                    if ("success".equals(poJSON.get("result"))) {
                        if (po().Master().getConvertedTransactionStatus().equals(PurchaseOrderStatus.OPEN)) {
                            if (ShowMessageFX.YesNo(null, psFormName, "Do you want to confirm this transaction?")) {
                                po().setWithUI(true);
                                if ("success".equals((poJSON = po().ConfirmTransaction("")).get("result"))) {
                                    ShowMessageFX.Information((String) poJSON.get("message"), psFormName, null);
                                } else {
                                    ShowMessageFX.Warning((String) poJSON.get("message"), psFormName, null);
                                    refreshSaved(lsSavedTransNo); //the transaction is saved: show it as it is now
                                    return;
                                }
                            }
                        }
                    }

                    // Print Transaction Prompt
                    if (ShowMessageFX.YesNo(null, psFormName, "Do you want to print this transaction?")) {
                        poJSON = po().printTransaction(PurchaseOrderStaticData.Printing_CARSp_MCSp_General);
                        if (!"success".equals((String) poJSON.get("result"))) {
                            ShowMessageFX.Warning((String) poJSON.get("message"), "Print Purchase Order", null);
                        }
                    }
                    refreshSaved(lsSavedTransNo);
                    return;
                case "btnCancel":
                    //leave the field being typed in first, while its item row is still there
                    btnCancel.requestFocus();
                    if (isEditing() && !ShowMessageFX.YesNo(null, "Cancel Confirmation", "Are you sure you want to cancel?")) {
                        return;
                    }
                    closeScreen(); //the landing retrieves again, so nothing of the edit is kept
                    return;
                case "btnPrint":
                    poJSON = po().printTransaction(PurchaseOrderStaticData.Printing_CARSp_MCSp_General);
                    if ("error".equals((String) poJSON.get("result"))) {
                        ShowMessageFX.Warning((String) poJSON.get("message"), psFormName, null);
                    }
                    break;
                case "btnRetrieve":

                    loadTableMain();
//                    if (tfSupplier.getText().isEmpty()) {
//                        ShowMessageFX.Warning("Invalid to retrieve stock request, supplier is empty.", psFormName, null);
//                        return;
//                    }
//                    if (!tfSupplier.getText().isEmpty() && !tfSupplier.getText().isEmpty()) {
//                        loadTableMain();
//                    }
                    break;
                case "btnTransHistory":
                    if (pnEditMode != EditMode.READY && pnEditMode != EditMode.UPDATE) {
                        ShowMessageFX.Warning("No transaction status history to load!", psFormName, null);
                        return;
                    }

                    try {
                        po().ShowStatusHistory();
                    } catch (NullPointerException npe) {
                        Logger.getLogger(getClass().getName()).log(Level.SEVERE, MiscUtil.getException(npe), npe);
                        ShowMessageFX.Error("No transaction status history to load!", psFormName, null);
                    } catch (Exception ex) {
                        Logger.getLogger(getClass().getName()).log(Level.SEVERE, MiscUtil.getException(ex), ex);
                        ShowMessageFX.Error(MiscUtil.getException(ex), psFormName, null);
                    }
                    break;
                case "btnRecipient":
                    openRecipientModal();
                    break;
                case "btnConfirm":
                    runStatusAction("Confirm", "Are you sure you want to confirm this transaction?", false,
                            remarks -> po().ConfirmTransaction(remarks));
                    return;
                case "btnApprove":
                    runStatusAction("Approve", "Are you sure you want to approve this transaction?", false,
                            remarks -> po().ApproveTransaction(remarks));
                    return;
                case "btnVoid":
                    runStatusAction("Void", "Are you sure you want to void this transaction?", true,
                            remarks -> po().VoidTransaction(remarks));
                    return;
                case "btnCancelTransaction":
                    runStatusAction("Cancel", "Are you sure you want to cancel this transaction?", true,
                            remarks -> po().CancelTransaction(remarks));
                    return;
                case "btnClose":
                    closeScreen();
                    return;

                case "btnAddAttachment":
                    fileChooser = new FileChooser();
                    fileChooser.setTitle("Choose Image");
                    fileChooser.getExtensionFilters().addAll(
                            new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.gif", "*.pdf")
                    );
                    java.io.File selectedFile = fileChooser.showOpenDialog((Stage) btnAddAttachment.getScene().getWindow());

                    if (selectedFile != null) {
                        // Read image from the selected file
                        Path imgPath = selectedFile.toPath();
                        Image loimage = new Image(Files.newInputStream(imgPath));
                        imageView.setImage(loimage);

                        //Validate attachment
                        String imgPath2 = selectedFile.getName().toString();
                        for (int lnCtr = 0; lnCtr <= po().getTransactionAttachmentCount() - 1; lnCtr++) {
                            if (imgPath2.equals(po().TransactionAttachmentList(lnCtr).getModel().getFileName())
                                    && RecordStatus.ACTIVE.equals(po().TransactionAttachmentList(lnCtr).getModel().getRecordStatus())) {
                                ShowMessageFX.Warning(null, psFormName, "File name already exists.");
                                pnAttachment = lnCtr;
                                loadRecordAttachment(true);
                                return;
                            }
                        }

                        //Limit maximum pages of pdf to add
                        if (imgPath2.toLowerCase().endsWith(".pdf")) {
                            try (PDDocument document = PDDocument.load(selectedFile)) {
                                PDFRenderer pdfRenderer = new PDFRenderer(document);
                                int pageCount = document.getNumberOfPages();
                                if (pageCount > 20) {
                                    ShowMessageFX.Warning(null, psFormName, "PDF exceeds maximum allowed pages.");
                                    return;
                                }
                            }
                        }

//                            int lnTempRow = JFXUtil.getDetailTempRow(attachment_data,  po().addAttachment(imgPath2), 3);
//                            pnAttachment = lnTempRow;
                        pnAttachment = po().addAttachment(imgPath2);
                        //Copy file to Attachment path
                        po().copyFile(selectedFile.toString());
                        loadTableAttachment();
                        tblAttachments.getFocusModel().focus(pnAttachment);
                        tblAttachments.getSelectionModel().select(pnAttachment);
                    }
                    break;
                case "btnRemoveAttachment":
                    if (po().getTransactionAttachmentCount() <= 0) {
                        return;
                    } else {
                        for (int lnCtr = 0; lnCtr < po().getTransactionAttachmentCount(); lnCtr++) {
                            if (RecordStatus.INACTIVE.equals(po().TransactionAttachmentList(lnCtr).getModel().getRecordStatus())) {
                                if (pnAttachment == lnCtr) {
                                    return;
                                }
                            }
                        }
                    }
                    poJSON = po().removeAttachment(pnAttachment);
                    if ("error".equals((String) poJSON.get("result"))) {
                        ShowMessageFX.Warning(null, psFormName, (String) poJSON.get("message"));
                        return;
                    }
                    attachment_data.remove(tblAttachments.getSelectionModel().getSelectedIndex());
                    if (pnAttachment != 0) {
                        pnAttachment -= 1;
                    }
                    loadRecordAttachment(false);
                    loadTableAttachment();
                    if (attachment_data.size() <= 0) {
                        JFXUtil.clearTextFields(apAttachments);
                    }
                    initAttachmentsGrid();
                    break;
                case "btnArrowLeft":
                    slideImage(-1);
                    break;
                case "btnArrowRight":
                    slideImage(1);
                    break;
                default:
                    ShowMessageFX.Warning("Please contact admin to assist about no button available", psFormName, null);
                    break;
            }
            if (lsButton.equals("btnRetrieve") || lsButton.equals("btnAddAttachment") || lsButton.equals("btnRemoveAttachment")
                    || lsButton.equals("btnArrowRight") || lsButton.equals("btnArrowLeft") || lsButton.equals("btnRetrieve") || lsButton.equals("btnHistory") || lsButton.equals("btnRecipient")
                    || pnEditMode == EditMode.UNKNOWN) {
            } else {
                loadRecordMaster();
                loadTableDetail();
                loadTableAttachment();
            }
            initButtons(pnEditMode);
            initFields(pnEditMode);
        } catch (CloneNotSupportedException | ExceptionInInitializerError | SQLException | GuanzonException | ParseException | NullPointerException ex) {
            Logger.getLogger(PurchaseOrderMP_PreviewController.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(PurchaseOrderMP_PreviewController.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /** Opens the recipients screen of the current PO and waits until it is closed. */
    private void openRecipientModal() {
        if (pnEditMode != EditMode.READY && pnEditMode != EditMode.ADDNEW
                && pnEditMode != EditMode.UPDATE) {
            ShowMessageFX.Warning("Open or create a purchase order first.", psFormName, null);
            return;
        }
        String lsSupplier = po().Master().getSupplierID();
        if (lsSupplier == null || lsSupplier.isEmpty()) {
            ShowMessageFX.Warning("Select a supplier first.", psFormName, null);
            return;
        }

        try {
            URL loLocation = getClass().getResource(PurchaseOrderStaticData.RECIPIENT_FXML);
            if (loLocation == null) {
                throw new IOException("Screen not found: " + PurchaseOrderStaticData.RECIPIENT_FXML);
            }
            FXMLLoader loader = new FXMLLoader(loLocation);
            Parent root = loader.load();

            // Pass the data to the modal controller
            PurchaseOrderRecipientController modalController = loader.getController();
            modalController.setGRider(poApp);
            modalController.setPurchaseOrder(po());
            modalController.loadForm();   // must be AFTER the setters

            Stage modalStage = new Stage();
            modalStage.setTitle("Purchase Order Recipients");
            modalStage.initStyle(StageStyle.UNDECORATED);
            modalStage.initModality(Modality.APPLICATION_MODAL);
            modalStage.initOwner(tblVwOrderDetails.getScene().getWindow());

            // 1. MAKE THE MAIN ANCHOR MOVABLE
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

            // 2. APPLY SHADOW TO THE MAIN ANCHOR
            javafx.scene.effect.DropShadow shadow = new javafx.scene.effect.DropShadow();
            shadow.setColor(javafx.scene.paint.Color.rgb(0, 0, 0, 0)); // 0 opacity: not visible
            shadow.setRadius(15.0);
            shadow.setOffsetX(0.0);
            shadow.setOffsetY(5.0);
            root.setEffect(shadow);

            // 3. BOUNDARY FIX FOR UNDECORATED SHADOWS
            // To prevent the shadow from being clipped by the window edges,
            // the scene is made transparent.
            Scene scene = new Scene(root);
            scene.setFill(javafx.scene.paint.Color.TRANSPARENT);

            modalStage.setScene(scene);
            modalStage.setResizable(false);
            modalStage.showAndWait();

        } catch (IOException e) {
            Logger.getLogger(PurchaseOrderMP_PreviewController.class.getName())
                    .log(Level.SEVERE, "Failed to load Recipients Modal", e);
            ShowMessageFX.Error("Unable to open recipients window.", psFormName, null);
        }
    }

    /**
     * Attachment list. Built on the screen thread: the background task only
     * reset the image view (a screen control, which must not be changed off the
     * screen thread) and handed the rest back to the screen thread anyway.
     * Errors are logged now (they were silently ignored).
     */
    private void loadTableAttachment() {
        if (po() == null) {
            return; //the PO is handed over later (loadForm)
        }
        scaleFactor = 1.0;
        JFXUtil.resetImageBounds(imageView, stackPane1);
        attachment_data.clear();
        try {
            int lnCount = 0;
            for (int lnCtr = 0; lnCtr < po().getTransactionAttachmentCount(); lnCtr++) {
                if (RecordStatus.INACTIVE.equals(po().TransactionAttachmentList(lnCtr).getModel().getRecordStatus())) {
                    continue;
                }
                lnCount += 1;
                attachment_data.add(new ModelPOAttachment(String.valueOf(lnCount),
                        String.valueOf(po().TransactionAttachmentList(lnCtr).getModel().getFileName()),
                        String.valueOf(lnCtr)));
            }
            int lnTempRow = JFXUtil.getDetailRow(attachment_data, pnAttachment, 3); //this method is used only when Reverse is applied
            if (lnTempRow < 0 || lnTempRow >= attachment_data.size()) {
                if (!attachment_data.isEmpty()) {
                    /* FOCUS ON FIRST ROW */
                    JFXUtil.selectAndFocusRow(tblAttachments, 0);
                    pnAttachment = Integer.parseInt(attachment_data.get(0).getIndex03());
                    loadRecordAttachment(true);
                }
            } else {
                /* FOCUS ON THE ROW THAT pnAttachment POINTS TO */
                JFXUtil.selectAndFocusRow(tblAttachments, lnTempRow);
                pnAttachment = Integer.parseInt(attachment_data.get(tblAttachments.getSelectionModel().getSelectedIndex()).getIndex03());
                loadRecordAttachment(true);
            }
            if (attachment_data.isEmpty()) {
                loadRecordAttachment(false);
            }
        } catch (Exception ex) {
            Logger.getLogger(PurchaseOrderMP_PreviewController.class.getName()).log(Level.SEVERE, "Unable to load the attachments", ex);
        }

        if (attachment_data.isEmpty()) {
            Label loPlaceholder = new Label("NO RECORD TO LOAD");
            loPlaceholder.setStyle("-fx-font-size: 10px;");
            tblAttachments.setPlaceholder(loPlaceholder);
        } else {
            tblAttachments.toFront();
        }
    }

    public void loadRecordAttachment(boolean lbloadImage) {
        if (po() == null) {
            return; //the PO is handed over later (loadForm)
        }
        try {
            boolean lbShow2 = pnEditMode == EditMode.UPDATE || pnEditMode == EditMode.ADDNEW;
            JFXUtil.setDisabled(!lbShow2, cmbAttachmentType, btnAddAttachment, btnRemoveAttachment);
            if (attachment_data.size() > 0) {
                tfAttachmentNo.setText(attachment_data.get(tblAttachments.getSelectionModel().getSelectedIndex()).getIndex01());
                String lsAttachmentType = po().TransactionAttachmentList(pnAttachment).getModel().getDocumentType();
                if (lsAttachmentType.equals("")) {
                    po().TransactionAttachmentList(pnAttachment).getModel().setDocumentType(DocumentType.OTHER);
                    lsAttachmentType = po().TransactionAttachmentList(pnAttachment).getModel().getDocumentType();
                }
                int lnAttachmentType = 0;
                lnAttachmentType = Integer.parseInt(lsAttachmentType);
                cmbAttachmentType.getSelectionModel().select(lnAttachmentType);
                if (lbloadImage) {
                    try {
                        String filePath = (String) attachment_data.get(tblAttachments.getSelectionModel().getSelectedIndex()).getIndex02();
                        String filePath2 = "";

                        // in server
                        if (po().TransactionAttachmentList(pnAttachment).getModel().getImagePath() != null && !"".equals(po().TransactionAttachmentList(pnAttachment).getModel().getImagePath())) {
                            filePath2 = po().TransactionAttachmentList(pnAttachment).getModel().getImagePath() + "/" + (String) attachment_data.get(tblAttachments.getSelectionModel().getSelectedIndex()).getIndex02();
                        } else {
                            filePath2 = System.getProperty("sys.default.path.temp.attachments") + "/" + (String) attachment_data.get(tblAttachments.getSelectionModel().getSelectedIndex()).getIndex02();
                        }

                        if (filePath != null && !filePath.isEmpty()) {
                            Path imgPath = Paths.get(filePath2);
                            String convertedPath = imgPath.toUri().toString();
                            boolean isPdf = filePath.toLowerCase().endsWith(".pdf");

                            // Clear previous content
                            stackPane1.getChildren().clear();
                            if (!isPdf) {
                                // ----- IMAGE VIEW -----
                                Image loimage = new Image(convertedPath);
                                imageView.setImage(loimage);
                                JFXUtil.adjustImageSize(loimage, imageView, ldstackPaneWidth, ldstackPaneHeight);

                                PauseTransition delay = new PauseTransition(Duration.seconds(2)); // 2-second delay
                                delay.setOnFinished(event -> {
                                    Platform.runLater(() -> {
                                        JFXUtil.stackPaneClip(stackPane1);
                                    });
                                });
                                delay.play();

                                // Add ImageView directly to stackPane
                                stackPane1.getChildren().add(imageView);
                                stackPane1.getChildren().addAll(btnArrowLeft, btnArrowRight);

                                // Align buttons on top
                                StackPane.setAlignment(btnArrowLeft, Pos.CENTER_LEFT);
                                StackPane.setAlignment(btnArrowRight, Pos.CENTER_RIGHT);

                                // Optional: add some margin
                                StackPane.setMargin(btnArrowLeft, new Insets(0, 0, 0, 10));
                                StackPane.setMargin(btnArrowRight, new Insets(0, 10, 0, 0));

                            } else {
                                // ----- PDF VIEW -----
                                JFXUtil.PDFViewConfig(filePath2, stackPane1, btnArrowLeft, btnArrowRight, ldstackPaneWidth, ldstackPaneHeight);
                            }
                        } else {
                            imageView.setImage(null);
                        }

                    } catch (Exception e) {
                        imageView.setImage(null);
                    }
                }
            } else {
                if (!lbloadImage) {
                    imageView.setImage(null);
                    // Clear previous content
                    stackPane1.getChildren().clear();
                    // Add ImageView directly to stackPane
                    stackPane1.getChildren().add(imageView);
                    stackPane1.getChildren().addAll(btnArrowLeft, btnArrowRight);
                    Platform.runLater(() -> JFXUtil.stackPaneClip(stackPane1));
                    pnAttachment = 0;
                }
            }
        } catch (Exception ex) {
        }
    }

    /** Searchable-field marks, button shortcuts and the unsaved-changes guard. */
    private void initScreenKeys() {
        //leaving Supplier (in this window) ends the "start at Supplier" of a new PO
        tfSupplier.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused && pbFocusHeader && tfSupplier.getScene() != null
                    && tfSupplier.getScene().getWindow() != null && tfSupplier.getScene().getWindow().isFocused()) {
                pbFocusHeader = false;
            }
        });
        SearchFields.mark(tfSupplier, tfDestination, tfReferenceNo, tfTerm, tfBarcode, tfDescription);
        //clicking Search keeps the focus (and the typed text) in the field
        btnSearch.setFocusTraversable(false);
        ScreenShortcuts.tabOutOf(taRemarks);
        //Esc: close (read-only) or cancel the update
        ScreenShortcuts.on(AnchorMain)
                .add(btnCancel, "Esc");
        ScreenShortcuts.guardClose(AnchorMain, this::isEditing, this::confirmDiscard);
    }

    /** Description of the PO's term; empty when no term is set. */
    private String termText() throws SQLException, GuanzonException {
        if (isEmpty(po().Master().getTermCode())) {
            return "";
        }
        String lsTerm = po().Master().Term().getDescription();
        return lsTerm == null ? "" : lsTerm;
    }

    private boolean isEditing() {
        return pnEditMode == EditMode.ADDNEW || pnEditMode == EditMode.UPDATE;
    }

    /** True when there is nothing to lose, or the user agrees to discard the changes. */
    private boolean confirmDiscard() {
        return !isEditing()
                || ShowMessageFX.YesNo("This purchase order has unsaved changes. Discard them?", psFormName, null);
    }

    /** cas-app asks before closing this screen's tab while a PO is new or being edited. */
    @Override
    public boolean hasUnsavedChanges() {
        return isEditing();
    }

    /** PO status with the screen mode: "NEW", "OPEN (EDITING)", "OPEN". */
    private String statusText() {
        if (pnEditMode == EditMode.ADDNEW) {
            return "NEW";
        }
        if (pnEditMode == EditMode.UPDATE) {
            return po().getStatusValue() + " (EDITING)";
        }
        if (pnEditMode == EditMode.READY) {
            return po().getStatusValue();
        }
        return "UNKNOWN";
    }

    private void initTextFieldFocus() {
        List<TextField> loTxtField = Arrays.asList(tfReferenceNo, tfDiscountRate, tfDiscountAmount,
                tfAdvancePRate, tfAdvancePAmount, tfCost, tfOrderQuantity,tfAddDesc, tfDestination, tfSupplier, tfTerm,tfAddDesc);
        loTxtField.forEach(tf -> tf.focusedProperty().addListener(txtField_Focus));

        tfBarcode.setOnMouseClicked(e -> activeField = tfBarcode);
        tfDescription.setOnMouseClicked(e -> activeField = tfDescription);
        tfSupplier.setOnMouseClicked(e -> activeField = tfSupplier);
        tfDestination.setOnMouseClicked(e -> activeField = tfDestination);
        tfTerm.setOnMouseClicked(e -> activeField = tfTerm);
    }

    /**
     * Leaving Destination: an empty field goes back to the user's branch, loaded
     * as a real record (it used to show the branch code as plain text while the
     * PO kept its old destination). Text that was typed but not searched is not
     * kept: the field always shows the destination the PO will be saved with.
     */
    private void leaveDestination(String fsValue) {
        if (pnEditMode != EditMode.ADDNEW && pnEditMode != EditMode.UPDATE) {
            return;
        }
        try {
            if (fsValue == null || fsValue.trim().isEmpty()) {
                JSONObject loJSON = po().SelectDestination(poApp.getBranchCode());
                if (!"success".equals(loJSON.get("result"))) {
                    ShowMessageFX.Warning((String) loJSON.get("message"), psFormName, null);
                }
            }
            String lsName = isEmpty(po().Master().getDestinationID()) ? "" : po().Master().Branch().getBranchName();
            tfDestination.setText(lsName == null ? "" : lsName);
        } catch (SQLException | GuanzonException ex) {
            Logger.getLogger(PurchaseOrderMP_PreviewController.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /** True while the screen shows the PO's supplier (the text listener must not clear it). */
    private boolean pbShowingSupplier;

    /**
     * Leaving Supplier or Term: text that was typed but not searched or picked
     * is not kept; the field shows the record the PO will be saved with. An
     * emptied Supplier is left to the text listener (it clears the supplier).
     */
    private void leaveLookup(String fsFieldId, String fsValue) {
        if (!isEditing()) {
            return;
        }
        try {
            if ("tfSupplier".equals(fsFieldId)) {
                if (fsValue == null || fsValue.trim().isEmpty()) {
                    return;
                }
                String lsName = isEmpty(po().Master().getSupplierID()) ? "" : po().Master().Supplier().getCompanyName();
                pbShowingSupplier = true;
                try {
                    tfSupplier.setText(lsName == null ? "" : lsName);
                } finally {
                    pbShowingSupplier = false;
                }
            } else if ("tfTerm".equals(fsFieldId)) {
                String lsTerm = isEmpty(po().Master().getTermCode()) ? "" : po().Master().Term().getDescription();
                tfTerm.setText(lsTerm == null ? "" : lsTerm);
            }
        } catch (SQLException | GuanzonException ex) {
            Logger.getLogger(PurchaseOrderMP_PreviewController.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void initTextAreaFocus() {
        taRemarks.focusedProperty().addListener(txtArea_Focus);
    }
    final ChangeListener<? super Boolean> txtField_Focus = (o, ov, nv) -> {
        TextField loTextField = (TextField) ((ReadOnlyBooleanPropertyBase) o).getBean();
        String lsTextFieldID = loTextField.getId();
        String lsValue = loTextField.getText();
        if (lsValue == null) {
            return;
        }
        poJSON = new JSONObject();
        if (!nv) {
            /*Lost Focus*/
            switch (lsTextFieldID) {
                case "tfReferenceNo":
                    po().Master().setReference(lsValue);
                    break;
                case "tfDiscountRate":
                    lsValue = JFXUtil.removeComma(lsValue);
                    poJSON = po().setDiscountRate(lsValue);
                    if (!"success".equals((String) poJSON.get("result"))) {
                        ShowMessageFX.Warning((String) poJSON.get("message"), psFormName, null);
                    }
                    tfDiscountRate.setText(CustomCommonUtil.setIntegerValueToDecimalFormat(po().Master().getDiscount().doubleValue()));
                    tfNetAmount.setText(CustomCommonUtil.setIntegerValueToDecimalFormat(po().Master().getNetTotal(), true));
                    break;
                case "tfDiscountAmount":
                    lsValue = JFXUtil.removeComma(lsValue);
                    poJSON = po().setDiscountAmount(lsValue);
                    if (!"success".equals((String) poJSON.get("result"))) {
                        ShowMessageFX.Warning((String) poJSON.get("message"), psFormName, null);
                    }
                    tfDiscountAmount.setText(CustomCommonUtil.setIntegerValueToDecimalFormat(po().Master().getAdditionalDiscount(), true));
                    tfNetAmount.setText(CustomCommonUtil.setIntegerValueToDecimalFormat(po().Master().getNetTotal(), true));
                    break;
//                case "tfAdvancePRate":
//                    lsValue = JFXUtil.removeComma(lsValue);
//                    poJSON = po().setAdvancePaymentRate(lsValue);
//                    if (!"success".equals((String) poJSON.get("result"))) {
//                        ShowMessageFX.Warning((String) poJSON.get("message"), psFormName, null);
//                    }
//
//                    tfAdvancePRate.setText(CustomCommonUtil.setIntegerValueToDecimalFormat(po().Master().getDownPaymentRatesPercentage().doubleValue()));
//                    tfNetAmount.setText(CustomCommonUtil.setIntegerValueToDecimalFormat(po().Master().getNetTotal(), true));
//                    break;
//                case "tfAdvancePAmount":
//                    lsValue = JFXUtil.removeComma(lsValue);
//                    poJSON = po().setAdvancePaymentAmount(lsValue);
//                    if (!"success".equals((String) poJSON.get("result"))) {
//                        ShowMessageFX.Warning((String) poJSON.get("message"), psFormName, null);
//                    }
//                    tfAdvancePAmount.setText(CustomCommonUtil.setIntegerValueToDecimalFormat(po().Master().getDownPaymentRatesAmount(), true));
//                    tfNetAmount.setText(CustomCommonUtil.setIntegerValueToDecimalFormat(po().Master().getNetTotal(), true));
//                    break;
                case "tfOrderQuantity":
                    break;
                case "tfAddDesc":
                    poJSON = po().Detail(pnTblDetailRow).setDescription(lsValue);
                    if (!"success".equals((String) poJSON.get("result"))) {
                        ShowMessageFX.Warning((String) poJSON.get("message"), psFormName, null);
                    }
                    loadTableDetailAndSelectedRow();
                    break;
                case "tfCost":
                    lsValue = JFXUtil.removeComma(lsValue);
                    poJSON = po().Detail(pnTblDetailRow).setUnitPrice(Double.valueOf(lsValue));
                    if (!"success".equals((String) poJSON.get("result"))) {
                        ShowMessageFX.Warning((String) poJSON.get("message"), psFormName, null);
                    }
                    loadTableDetailAndSelectedRow();
                    break;
                case "tfDestination":
                    leaveDestination(lsValue);
                    break;
                case "tfSupplier":
                case "tfTerm":
                    leaveLookup(lsTextFieldID, lsValue);
                    break;
            }
        } else {
            loTextField.selectAll();
        }
    };
    final ChangeListener<? super Boolean> txtArea_Focus = (o, ov, nv) -> {
        TextArea loTextArea = (TextArea) ((ReadOnlyBooleanPropertyBase) o).getBean();
        String lsTextAreaID = loTextArea.getId();
        String lsValue = loTextArea.getText();
        if (lsValue == null) {
            return;
        }
        if (!nv) {
            /*Lost Focus*/
            switch (lsTextAreaID) {
                case "taRemarks":
                    po().Master().setRemarks(lsValue);
                    break;
            }
        } else {
            loTextArea.selectAll();
        }
    };

    private void initTextFieldKeyPressed() {
        List<TextField> loTxtField = Arrays.asList(tfAdvancePAmount, tfSupplier,
                tfReferenceNo, tfTerm, tfDiscountRate, tfDiscountAmount, tfTotalAmount,
                tfDestination, tfAdvancePRate,
                tfBarcode, tfDescription,
                tfBO, tfRO,
                tfCost, tfOrderQuantity,tfAddDesc);

        loTxtField.forEach(tf -> tf.setOnKeyPressed(event -> txtField_KeyPressed(event)));
    }

    /**
     * Whether the field still shows the record that is loaded (its id is set
     * and the text is what the screen displays for it), so Enter needs no search.
     */
    private boolean isLoadedValue(String fsFieldId, String fsText) throws SQLException, GuanzonException {
        if (fsText == null || fsText.trim().isEmpty()) {
            return false;
        }
        String lsLoaded;
        switch (fsFieldId) {
            case "tfReferenceNo":
                lsLoaded = po().Master().getReference();
                break;
            case "tfSupplier":
                lsLoaded = isEmpty(po().Master().getSupplierID()) ? null : po().Master().Supplier().getCompanyName();
                break;
            case "tfDestination":
                lsLoaded = isEmpty(po().Master().getDestinationID()) ? null : po().Master().Branch().getBranchName();
                break;
            case "tfTerm":
                lsLoaded = isEmpty(po().Master().getTermCode()) ? null : po().Master().Term().getDescription();
                break;
            case "tfBarcode":
            case "tfDescription":
                if (pnTblDetailRow < 0 || pnTblDetailRow >= po().getDetailCount()
                        || isEmpty(po().Detail(pnTblDetailRow).getStockID())) {
                    return false;
                }
                lsLoaded = "tfBarcode".equals(fsFieldId)
                        ? po().Detail(pnTblDetailRow).Inventory().getBarCode()
                        : po().Detail(pnTblDetailRow).Inventory().getDescription();
                break;
            default:
                return false;
        }
        return lsLoaded != null && !lsLoaded.isEmpty() && fsText.trim().equals(lsLoaded.trim());
    }

    private static boolean isEmpty(String fsValue) {
        return fsValue == null || fsValue.isEmpty();
    }

    /** Fields whose Enter / F3 opens a search (Tab only moves on). */
    private static boolean isSearchField(String fsFieldId) {
        switch (fsFieldId) {
            case "tfReferenceNo":
            case "tfSupplier":
            case "tfDestination":
            case "tfTerm":
            case "tfBarcode":
            case "tfDescription":
                return true;
            default:
                return false;
        }
    }

    private void txtField_KeyPressed(KeyEvent event) {
        TextField lsTxtField = (TextField) event.getSource();
        String txtFieldID = ((TextField) event.getSource()).getId();
        String lsValue = "";
        if (lsTxtField.getText() == null) {
            lsValue = "";
        } else {
            lsValue = lsTxtField.getText();
        }
        try {
            if (null != event.getCode()) {
                switch (event.getCode()) {
                    case TAB:
                    case ENTER:
                    case F3:
                        //Tab only moves to the next field; Enter and F3 open the search
                        if (event.getCode() == TAB && isSearchField(txtFieldID)) {
                            CommonUtils.SetNextFocus(lsTxtField);
                            event.consume();
                            break;
                        }
                        //Enter on a field still showing its loaded record (unique id present): no search, move on.
                        //F3 always searches.
                        if (event.getCode() == ENTER && isSearchField(txtFieldID) && isLoadedValue(txtFieldID, lsValue)) {
                            CommonUtils.SetNextFocus(lsTxtField);
                            event.consume();
                            break;
                        }
                        if (isSearchField(txtFieldID) && !searchField(txtFieldID, lsValue)) {
                            return; //supplier change not confirmed
                        }
                        switch (txtFieldID) {
                            case "tfCompany":
                            case "tfSupplier":
                            case "tfDestination":
                            case "tfReferenceNo":
                            case "tfTerm":
                            case "tfAdvancePAmount":
                            case "tfAdvancePRate":
                            case "tfDiscountRate":
                            case "tfDiscountAmount":
                            case "tfCost":
                                CommonUtils.SetNextFocus((TextField) event.getSource());
                                break;
                            case "tfOrderQuantity":
                                setOrderQuantityToDetail(tfOrderQuantity.getText());
                                poJSON = po().netTotalChecker(pnTblDetailRow);
                                if ("error".equals(poJSON.get("result"))) {
                                    ShowMessageFX.Warning((String) poJSON.get("message"), psFormName, null);
                                    tfOrderQuantity.setText("0");
                                    loadRecordMaster();
                                    loadTableDetailAndSelectedRow();
                                    break;
                                }
                                if (!detail_data.isEmpty() && pnTblDetailRow < detail_data.size() - 1) {
                                    pnTblDetailRow++;
                                }
                                CommonUtils.SetNextFocus((TextField) event.getSource());
                                loadTableDetailAndSelectedRow();
                                break;
                            case "tfAddDesc":
                                CommonUtils.SetNextFocus((TextField) event.getSource());
                                break;
                        }
                        event.consume();
                        break;
                    case F4:
                        //Barcode / Description: search the items of all suppliers
                        if ("tfBarcode".equals(txtFieldID) || "tfDescription".equals(txtFieldID)) {
                            searchItem(lsTxtField, lsValue, "tfBarcode".equals(txtFieldID), false);
                        }
                        event.consume();
                        break;
                    case UP:
                        setOrderQuantityToDetail(tfOrderQuantity.getText());
                        poJSON = po().netTotalChecker(pnTblDetailRow);
                        if ("error".equals(poJSON.get("result"))) {
                            ShowMessageFX.Warning((String) poJSON.get("message"), psFormName, null);
                            tfOrderQuantity.setText("0");
                            loadRecordMaster();
                            loadTableDetailAndSelectedRow();
                            break;
                        }
                        if (!lsTxtField.equals("tfBarcode") && !lsTxtField.equals("tfDescription")) {
                            if (pnTblDetailRow > 0 && !detail_data.isEmpty()) {
                                pnTblDetailRow--;
                            }
                        }

                        // Prevent going from 'tfOrderQuantity' to 'taRemarks'
                        if (!lsTxtField.equals("tfBarcode") && !lsTxtField.equals("tfOrderQuantity")) {
                            CommonUtils.SetPreviousFocus((TextField) event.getSource());
                        }
                        loadTableDetailAndSelectedRow();
                        event.consume();
                        break;
                    case DOWN:
                        setOrderQuantityToDetail(tfOrderQuantity.getText());
                        poJSON = po().netTotalChecker(pnTblDetailRow);
                        if ("error".equals(poJSON.get("result"))) {
                            ShowMessageFX.Warning((String) poJSON.get("message"), psFormName, null);
                            tfOrderQuantity.setText("0");
                            loadRecordMaster();
                            loadTableDetailAndSelectedRow();
                            break;
                        }
                        if ("tfOrderQuantity".equals(lsTxtField.getId())) {
                            if (!detail_data.isEmpty() && pnTblDetailRow < detail_data.size() - 1) {
                                pnTblDetailRow++;
                            }
                        }
                        CommonUtils.SetNextFocus(lsTxtField);
                        loadTableDetailAndSelectedRow();
                        event.consume(); // Consume event after handling focus
                        break;
                    default:
                        break;

                }
            }
        } catch (ExceptionInInitializerError | SQLException | CloneNotSupportedException | GuanzonException | NullPointerException ex) {
            Logger.getLogger(PurchaseOrderMP_PreviewController.class
                    .getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void setOrderQuantityToDetail(String fsValue) {
        if (fsValue.isEmpty()) {
            fsValue = "0";
        }
        if (Integer.parseInt(fsValue) < 0) {
            ShowMessageFX.Warning("Invalid Order Quantity", psFormName, null);
            fsValue = "0";
        }
        if (tfOrderQuantity.isFocused()) {
            if (tfBarcode.getText().isEmpty()) {
                ShowMessageFX.Warning("Invalid action, Please enter barcode first. ", psFormName, null);
                tfBarcode.requestFocus();
                fsValue = "0";
            }
        }

        if (pnTblDetailRow < 0) {
            fsValue = "0";
            ShowMessageFX.Warning("Invalid row to update.", psFormName, null);
            clearDetailFields();
            int detailCount = po().getDetailCount();
            pnTblDetailRow = detailCount > 0 ? detailCount - 1 : 0;
        }
        double lnRequestQuantity = 0;
        try {
            System.out.println("SOURCE CODE: " + po().Detail(pnTblDetailRow).getSouceCode());
            if (PurchaseOrderStatus.SourceCode.POQUOTATION.equals(po().Detail(pnTblDetailRow).getSouceCode())) {
                lnRequestQuantity = po().Detail(pnTblDetailRow).POQuotationDetail().getQuantity();
            } else {
                lnRequestQuantity = po().Detail(pnTblDetailRow).InvStockRequestDetail().getApproved();
            }
            if (!po().Detail(pnTblDetailRow).getSouceNo().isEmpty()) {
                if (Integer.parseInt(fsValue) > lnRequestQuantity) {
                    ShowMessageFX.Warning("Invalid order quantity entered. The item is from a stock request, and the order quantity must not be greater than the requested quantity.", psFormName, null);
                    fsValue = "0";
                }
            }
        } catch (GuanzonException | SQLException ex) {
            Logger.getLogger(PurchaseOrderMP_PreviewController.class.getName()).log(Level.SEVERE, null, ex);
        }
        po().Detail(pnTblDetailRow).setQuantity(Double.valueOf(fsValue));
        tfOrderQuantity.setText(String.valueOf(po().Detail(pnTblDetailRow).getQuantity().intValue()));
    }

    private void initTextFieldPattern() {
        JFXUtil.inputDecimalOnly(tfDiscountRate, tfAdvancePRate);
        JFXUtil.setCommaFormatter(tfCost, tfDiscountAmount, tfAdvancePAmount);
        CustomCommonUtil.inputIntegersOnly(tfOrderQuantity);
    }

    private void initDatePickerActions() {
        dpTransactionDate.setOnAction(e -> {
                    if (pnEditMode == EditMode.ADDNEW || pnEditMode == EditMode.UPDATE) {
                        LocalDate selectedLocalDate = dpTransactionDate.getValue();
                        LocalDate transactionDate = new java.sql.Date(po().Master().getTransactionDate().getTime()).toLocalDate();
                        if (selectedLocalDate == null) {
                            return;
                        }

                        LocalDate dateNow = LocalDate.now();
                        psOldDate = CustomCommonUtil.formatLocalDateToShortString(transactionDate);
                        String lsReferNo = tfReferenceNo.getText().trim();
                        boolean approved = true;
                        if (pnEditMode == EditMode.UPDATE) {
                            psOldDate = CustomCommonUtil.formatLocalDateToShortString(transactionDate);
                            if (selectedLocalDate.isAfter(dateNow)) {
                                ShowMessageFX.Warning("Invalid to future date.", psFormName, null);
                                approved = false;
                            }

                            if (selectedLocalDate.isBefore(transactionDate) && lsReferNo.isEmpty()) {
                                ShowMessageFX.Warning("Invalid to backdate. Please enter a reference number first.", psFormName, null);
                                approved = false;
                            }
                            if (selectedLocalDate.isBefore(transactionDate) && !lsReferNo.isEmpty()) {
                                boolean proceed = ShowMessageFX.YesNo(
                                        "You are changing the transaction date\n"
                                                + "If YES, seek approval to proceed with the changed date.\n"
                                                + "If NO, the transaction date will be remain.",
                                        psFormName, null
                                );
                                if (proceed) {
                                    if (poApp.getUserLevel() <= UserRight.ENCODER) {
                                        poJSON = ShowDialogFX.getUserApproval(poApp);
                                        if (!"success".equals((String) poJSON.get("result"))) {
                                            approved = false;
                                            return;
                                        } else {
                                            if (Integer.parseInt(poJSON.get("nUserLevl").toString()) <= UserRight.ENCODER) {
                                                ShowMessageFX.Warning("User is not an authorized approving officer..", psFormName, null);
                                                approved = false;
                                                return;
                                            }
                                            po().setApproving((String) poJSON.get("sUserIDxx"));
                                        }
                                    }
                                } else {
                                    approved = false;
                                }
                            }
                        }
                        if (pnEditMode == EditMode.ADDNEW) {
                            if (selectedLocalDate.isAfter(dateNow)) {
                                ShowMessageFX.Warning("Invalid to future date.", psFormName, null);
                                approved = false;
                            }
                            if (selectedLocalDate.isBefore(dateNow) && lsReferNo.isEmpty()) {
                                ShowMessageFX.Warning("Invalid to backdate. Please enter a reference number first.", psFormName, null);
                                approved = false;
                            }

                            if (selectedLocalDate.isBefore(dateNow) && !lsReferNo.isEmpty()) {
                                boolean proceed = ShowMessageFX.YesNo(
                                        "You selected a backdate with a reference number.\n\n"
                                                + "If YES, seek approval to proceed with the backdate.\n"
                                                + "If NO, the transaction date will be reset to today.",
                                        "Backdate Confirmation", null
                                );
                                if (proceed) {
                                    if (poApp.getUserLevel() <= UserRight.ENCODER) {
                                        poJSON = ShowDialogFX.getUserApproval(poApp);
                                        if (!"success".equals((String) poJSON.get("result"))) {
                                            approved = false;
                                            return;
                                        } else {
                                            if (Integer.parseInt(poJSON.get("nUserLevl").toString()) <= UserRight.ENCODER) {
                                                ShowMessageFX.Warning("User is not an authorized approving officer..", psFormName, null);
                                                approved = false;
                                                return;
                                            }
                                        }
                                    }
                                } else {
                                    approved = false;
                                }
                            }
                        }
                        if (approved) {
                            po().Master().setTransactionDate(
                                    SQLUtil.toDate(selectedLocalDate.toString(), SQLUtil.FORMAT_SHORT_DATE));
                        } else {
                            if (pnEditMode == EditMode.ADDNEW) {
                                dpTransactionDate.setValue(dateNow);
                                po().Master().setTransactionDate(
                                        SQLUtil.toDate(dateNow.toString(), SQLUtil.FORMAT_SHORT_DATE));
                            } else if (pnEditMode == EditMode.UPDATE) {
                                po().Master().setTransactionDate(
                                        SQLUtil.toDate(psOldDate, SQLUtil.FORMAT_SHORT_DATE));
                            }

                        }
                        dpTransactionDate.setValue(CustomCommonUtil.parseDateStringToLocalDate(
                                SQLUtil.dateFormat(po().Master().getTransactionDate(), SQLUtil.FORMAT_SHORT_DATE)));
                    }
                }
        );

        dpExpectedDlvrDate.setOnAction(e
                        -> {
                    if (pnEditMode == EditMode.ADDNEW || pnEditMode == EditMode.UPDATE) {
                        if (dpExpectedDlvrDate.getValue() != null) {
                            LocalDate selectedLocalDate = dpExpectedDlvrDate.getValue();
                            Date selectedDate = SQLUtil.toDate(selectedLocalDate.toString(), SQLUtil.FORMAT_SHORT_DATE);
                            Date transactionDate = po().Master().getTransactionDate();
                            LocalDate transactionLocalDate = LocalDate.now();

                            if (selectedDate.before(transactionDate)) {
                                ShowMessageFX.Warning("Please select an expected delivery date that is on or after the transaction date.", "Invalid Expected Delivery Date", null);
                                dpExpectedDlvrDate.setValue(transactionLocalDate);
                                po().Master().setExpectedDate(transactionDate);
                                return;
                            }

                            po().Master().setExpectedDate(selectedDate);
                        }
                    }
                }
        );
    }

    private void initCheckBoxActions() {
        chkbAdvancePayment.setOnAction(event -> {
            if (pnEditMode == EditMode.ADDNEW || pnEditMode == EditMode.UPDATE) {
                poJSON = new JSONObject();
                if (tfTotalAmount.getText().isEmpty()
                        || Double.parseDouble(tfTotalAmount.getText().replace(",", "")) > 0.0000) {

                    if (chkbAdvancePayment.isSelected()) {
                        chkbAdvancePayment.setSelected(true);
                        po().Master().setWithAdvPaym(true);
                    } else {
                        if (Double.parseDouble(tfAdvancePAmount.getText().replace(",", "")) > 0.0000
                                || Double.parseDouble(tfAdvancePRate.getText().replace(",", "")) > 0.00) {
                            if (ShowMessageFX.YesNo("An advance payment amount has already been entered. Are you sure you want to unselect it?", psFormName, null)) {
                                po().Master().setWithAdvPaym(false);
                                chkbAdvancePayment.setSelected(false);
                                po().Master().setDownPaymentRatesAmount(0.0000);
                                po().Master().setDownPaymentRatesPercentage(0.00);
                                tfAdvancePRate.setText(CustomCommonUtil.setIntegerValueToDecimalFormat(po().Master().getDownPaymentRatesPercentage().doubleValue()));
                                tfAdvancePAmount.setText(CustomCommonUtil.setIntegerValueToDecimalFormat(po().Master().getDownPaymentRatesAmount(), true));
                                po().computeNetTotal();
                                tfNetAmount.setText(CustomCommonUtil.setIntegerValueToDecimalFormat(po().Master().getNetTotal(), true));
                            } else {
                                chkbAdvancePayment.setSelected(true);
                                po().Master().setWithAdvPaym(true);
                                return;
                            }
                        } else {
                            po().Master().setWithAdvPaym(false);
                            chkbAdvancePayment.setSelected(false);
                        }
                    }
                } else {
                    ShowMessageFX.Warning("Advance payment cannot be entered until the total amount is greater than 0.0000.", psFormName, null);
                    po().Master().setWithAdvPaym(false);
                    chkbAdvancePayment.setSelected(false);
                }
                initFields(pnEditMode);
            }
        });
    }
    public void loadTableDetailFromMain() {
        try {
            poJSON = new JSONObject();
            if (po().getEditMode() == EditMode.READY || po().getEditMode() == EditMode.UPDATE
                    || po().getEditMode() == EditMode.UPDATE) {
                po().loadAttachments();

                tfAttachmentNo.clear();
                cmbAttachmentType.setItems(documentType);
                imageView.setImage(null);
                stackPaneClip();
                Platform.runLater(() -> {
                    loadTableAttachment();
                });
            }

        } catch (SQLException | GuanzonException ex) {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, MiscUtil.getException(ex), ex);
        }
    }


    /**
     * Cancel: discards the PO on screen and empties every field and table. The
     * PO controller is created again, so nothing from the discarded PO is shown
     * or carried to the next one.
     */
    private void cancelEntry() {
        pnEditMode = EditMode.UNKNOWN; //first, so the field actions ignore the clearing

        clearMasterFields();
        clearDetailFields();
        pbShowingSupplier = true;
        try {
            tfSupplier.setText("");
        } finally {
            pbShowingSupplier = false;
        }
        prevSupplier = "";
        activeField = null;

        //stock requests
        pnTblMainRow = -1;
        main_data.clear();
        showMainPage();

        //item lines
        detail_data.clear();
        tblVwOrderDetails.getSelectionModel().clearSelection();

        //attachments
        attachment_data.clear();
        cmbAttachmentType.getSelectionModel().clearSelection();
        resetImageBounds();
        loadRecordAttachment(false);

        if (initPurchaseOrder()) {
            po().Master().setSupplierID(""); //Browse searches by this: blank means any supplier
        } else {
            ShowMessageFX.Warning("Unable to reset the purchase order. Close this screen and open it again.",
                    psFormName, null);
        }
    }

    private void clearMasterFields() {
        /* Master Fields*/
        lblTransactionStatus.setText("UNKNOWN");
        pnTblDetailRow = -1;
        dpTransactionDate.setValue(null);
        dpExpectedDlvrDate.setValue(null);
        taRemarks.setText("");
        psOldDate = "";
        CustomCommonUtil.setSelected(false, chkbAdvancePayment);
        cmbOrderType.getSelectionModel().clearSelection();
        CustomCommonUtil.setText("", tfTransactionNo,
                tfDestination, tfReferenceNo, tfTerm);
        CustomCommonUtil.setText("0.0000", tfTotalAmount, tfAdvancePAmount, tfAdvancePRate,
                tfDiscountAmount, tfDiscountRate, tfNetAmount);
        imageView.setImage(null);
        tfAttachmentNo.clear();
    }

    private void clearDetailFields() {
        CustomCommonUtil.setText("", tfBarcode, tfDescription, tfBrand, tfModel,
                tfColor, tfVariant, tfInventoryType,  tfClass, tfAMC, tfAddDesc
        );
        CustomCommonUtil.setText("0", tfOrderQuantity, tfQOH, tfRequestQuantity, tfBO, tfRO, tfROQ);
        tfCost.setText("0.0000");
    }

    /**
     * Buttons follow the edit mode:
     * <ul>
     * <li>UNKNOWN (before the transaction is loaded): Close only</li>
     * <li>READY (preview): Close, History, Recipients, Print (by status), Update (Open / Returned)</li>
     * <li>UPDATE: Search, Save, Cancel, History, Recipients</li>
     * </ul>
     */
    private void initButtons(int fnEditMode) {
        pbFocusHeader = false;

        boolean lbReady = fnEditMode == EditMode.READY;
        boolean lbEdit = fnEditMode == EditMode.ADDNEW || fnEditMode == EditMode.UPDATE;

        //start from nothing shown, then show what the mode allows
        CustomCommonUtil.setVisible(false, btnUpdate, btnPrint, btnConfirm, btnApprove, btnVoid, btnCancelTransaction);
        CustomCommonUtil.setManaged(false, btnUpdate, btnPrint, btnConfirm, btnApprove, btnVoid, btnCancelTransaction);

        CustomCommonUtil.setVisible(lbEdit, btnSearch, btnSave, btnCancel);
        CustomCommonUtil.setManaged(lbEdit, btnSearch, btnSave, btnCancel);

        CustomCommonUtil.setVisible(!lbEdit, btnClose);
        CustomCommonUtil.setManaged(!lbEdit, btnClose);

        boolean lbHistory = lbReady || fnEditMode == EditMode.UPDATE;
        btnTransHistory.setVisible(lbHistory);
        btnTransHistory.setManaged(lbHistory);

        boolean lbRecipient = lbReady || lbEdit;
        btnRecipient.setVisible(lbRecipient);
        btnRecipient.setManaged(lbRecipient);

        if (fnEditMode == EditMode.UNKNOWN || po() == null) {
            lblTransactionStatus.setText(statusText());
            return;
        }
        lblTransactionStatus.setText(statusText());

        if (lbReady) {
            btnPrint.setText("1".equals(String.valueOf(po().Master().getPrint())) ? "Reprint" : "Print");
            String lsStatus = po().Master().getConvertedTransactionStatus();
            switch (lsStatus) {
                case PurchaseOrderStatus.OPEN:
                case PurchaseOrderStatus.RETURNED:
                    //Open: Update (as the landing), Confirm and Cancel (instead of Void)
                    CustomCommonUtil.setVisible(true, btnUpdate, btnPrint, btnConfirm, btnCancelTransaction);
                    CustomCommonUtil.setManaged(true, btnUpdate, btnPrint, btnConfirm, btnCancelTransaction);
                    break;
                case PurchaseOrderStatus.CONFIRMED:
                    //Confirmed: Approve and Void
                    CustomCommonUtil.setVisible(true, btnPrint, btnApprove, btnVoid);
                    CustomCommonUtil.setManaged(true, btnPrint, btnApprove, btnVoid);
                    break;
                case PurchaseOrderStatus.APPROVED:
                    btnPrint.setVisible(true);
                    btnPrint.setManaged(true);
                    break;
                default:
                    break;
            }
        }
    }

    /** Entry fields are only enabled while updating; the supplier is never changed here. */
    private void initFields(int fnEditMode) {
        boolean lbShow = (fnEditMode == EditMode.ADDNEW || fnEditMode == EditMode.UPDATE);
        CustomCommonUtil.setDisable(!lbShow,
                dpTransactionDate, tfDestination, taRemarks,
                dpExpectedDlvrDate, tfReferenceNo, tfTerm,
                chkbAdvancePayment, cmbOrderType, tfAddDesc);

        CustomCommonUtil.setDisable(true, tfSupplier, tfDiscountRate, tfDiscountAmount,
                tfAdvancePRate, tfAdvancePAmount, tfOrderQuantity, tfBarcode, tfDescription);

        if (!lbShow) {
            CustomCommonUtil.setDisable(true, tfCost, tfRequestQuantity);
            return;
        }

        if (!tfReferenceNo.getText().isEmpty()) {
            dpTransactionDate.setDisable(!lbShow);
        }
        if (pnTblDetailRow >= 0 && pnTblDetailRow < po().Detail().size()) {
            try {
                if (!PurchaseOrderStaticData.INVENTORY_TYPE_FREE_ENTRY.equals(inventoryTypeOf(pnTblDetailRow))) {
                    CustomCommonUtil.setDisable(!lbShow, tfBarcode, tfDescription);
                    boolean isSourceNotEmpty = !po().Detail(pnTblDetailRow).getSouceNo().isEmpty();
                    tfBarcode.setDisable(isSourceNotEmpty);
                    tfDescription.setDisable(isSourceNotEmpty);
                }
                CustomCommonUtil.setDisable(!lbShow, tfOrderQuantity);
            } catch (GuanzonException | SQLException ex) {
                Logger.getLogger(PurchaseOrderMP_PreviewController.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        if (chkbAdvancePayment.isSelected()) {
            CustomCommonUtil.setDisable(!lbShow, tfAdvancePRate, tfAdvancePAmount);
        }
        if (po().Master().getTranTotal().doubleValue() > 0.0000) {
            tfDiscountRate.setDisable(!lbShow);
            tfDiscountAmount.setDisable(!lbShow);
        }
    }

    /** The stock request table is not in the preview FXML. */
    private void clearStockRequestTable() {
        if (tblVwStockRequest == null) {
            return;
        }
        tblVwStockRequest.getItems().clear();
        tblVwStockRequest.setPlaceholder(new Label("NO RECORD TO LOAD"));
    }

    /**
     * Approved stock requests of the supplier (left table). Read on the screen
     * thread: it used to run on a background thread while the screen used the
     * same PO and database connection (the race behind the earlier
     * NullPointerException in the item table). One query, so it is quick.
     */
    private void loadTableMain() {
        if (tblVwStockRequest == null || btnRetrieve == null) {
            return; //the stock request panel is not in the preview FXML
        }
        btnRetrieve.setDisable(true);
        main_data.clear();
        try {
            JSONObject loJSON = po().getApprovedStockRequests();
            if ("success".equals(loJSON.get("result"))) {
                JSONArray laRequests = (JSONArray) loJSON.get("data");
                if (laRequests != null) {
                    for (Object loRow : laRequests) {
                        JSONObject loRequest = (JSONObject) loRow;
                        main_data.add(new ModelPurchaseOrder(
                                String.valueOf(main_data.size() + 1),
                                text(loRequest.get("sBranchNm")),
                                text(loRequest.get("dTransact")),
                                text(loRequest.get("sTransNox")),
                                text(loRequest.get("total_details")),
                                text(loRequest.get("sTransNox")),
                                "0",
                                text(loRequest.get("request_type")),
                                "",
                                ""));
                    }
                }
            }
        } catch (SQLException | GuanzonException ex) {
            Logger.getLogger(PurchaseOrderMP_PreviewController.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            btnRetrieve.setDisable(false);
        }
        showMainPage();
        selectTheExistedDetailFromMainTable();
    }

    private boolean pbMainPageListener;

    /** First page of main_data, or "NO RECORD TO LOAD". */
    private void showMainPage() {
        if (main_data.isEmpty()) {
            tblVwStockRequest.setItems(FXCollections.observableArrayList());
            tblVwStockRequest.setPlaceholder(new Label("NO RECORD TO LOAD"));
            if (pagination != null) {
                pagination.setVisible(false);
                pagination.setManaged(false);
            }
            return;
        }
        if (pagination != null) {
            if (!pbMainPageListener) {
                //added once (it was added again on every load)
                pagination.currentPageIndexProperty().addListener((obs, oldIndex, newIndex) -> createPage(newIndex.intValue()));
                pbMainPageListener = true;
            }
            pagination.setVisible(true);
            pagination.setManaged(true);
        }
        createPage(0);
        tblVwStockRequest.toFront();
    }

    private static String text(Object foValue) {
        return foValue == null ? "" : foValue.toString();
    }

    private Node createPage(int pageIndex) {
        int totalPages = (int) Math.ceil((double) main_data.size() / pnTblMain_Page);
        if (totalPages == 0) {
            totalPages = 1;
        }

        pageIndex = Math.max(0, Math.min(pageIndex, totalPages - 1));
        int fromIndex = pageIndex * pnTblMain_Page;
        int toIndex = Math.min(fromIndex + pnTblMain_Page, main_data.size());

        if (!main_data.isEmpty()) {
            tblVwStockRequest.setItems(FXCollections.observableArrayList(main_data.subList(fromIndex, toIndex)));
        }

        if (pagination != null) { // Replace with your actual Pagination variable
            pagination.setPageCount(totalPages);
            pagination.setCurrentPageIndex(pageIndex);
        }

        return tblVwStockRequest;
    }

    private void initTableStockRequest() {
        if (pnEditMode == EditMode.ADDNEW || pnEditMode == EditMode.UPDATE) {
            tblVwStockRequest.setEditable(true);
        } else {
            tblVwStockRequest.setEditable(false);
        }
        JFXUtil.setColumnCenter(tblRowNo, tblDate, tblReferenceNo, tblNoOfItems);
        JFXUtil.setColumnLeft(tblBranchName);
        JFXUtil.setColumnsIndexAndDisableReordering(tblVwStockRequest);

        initTableHighlithers();
    }

    private void initTableHighlithers() {
        tblVwOrderDetails.setRowFactory(tv -> new TableRow<ModelPurchaseOrderDetail>() {
            @Override
            protected void updateItem(ModelPurchaseOrderDetail item, boolean empty) {
                super.updateItem(item, empty);

                if (item == null || empty) {
                    setStyle("");
                } else if ("1".equals(item.getIndex10())) {
                    setStyle("-fx-background-color: #FAA0A0;");
                } else {
                    setStyle("");
                }
            }
        });
    }

    private void loadTableDetail() {
        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setMaxSize(50, 50);
        progressIndicator.setStyle("-fx-accent: #FF8201;");

        StackPane loadingPane = new StackPane(progressIndicator);
        loadingPane.setAlignment(Pos.CENTER);
        loadingPane.setStyle("-fx-background-color: transparent;");

        detail_data.clear();
        tblVwOrderDetails.setPlaceholder(loadingPane);
        progressIndicator.setVisible(true);

        Task<List<ModelPurchaseOrderDetail>> task = new Task<List<ModelPurchaseOrderDetail>>() {
            @Override
            protected List<ModelPurchaseOrderDetail> call() throws Exception {
                try {
                    int detailCount = po().getDetailCount();
                    if ((pnEditMode == EditMode.ADDNEW || pnEditMode == EditMode.UPDATE)) {
                        Model_PO_Detail lastDetail = po().Detail(detailCount - 1);
                        if (lastDetail.getStockID() != null && !lastDetail.getStockID().isEmpty()) {
                            po().AddDetail();
                            detailCount++;
                        }
                    }
                    double grandTotalAmount = 0.0000;
                    List<ModelPurchaseOrderDetail> detailsList = new ArrayList<>();

                    for (int lnCtr = 0; lnCtr < detailCount; lnCtr++) {
                        Model_PO_Detail orderDetail = po().Detail(lnCtr);
                        double lnTotalAmount = orderDetail.getUnitPrice().doubleValue() * orderDetail.getQuantity().intValue();
                        grandTotalAmount += lnTotalAmount;
                        double lnRequestQuantity = 0;
                        String status = "0";
                        double lnTotalQty = 0.0000;
                        switch (po().Detail(lnCtr).getSouceCode()) {
                            case PurchaseOrderStatus.SourceCode.STOCKREQUEST:
                                lnRequestQuantity = po().Detail(lnCtr).InvStockRequestDetail().getApproved();
                                lnTotalQty = (po().Detail(lnCtr).InvStockRequestDetail().getPurchase()
                                        + po().Detail(lnCtr).InvStockRequestDetail().getIssued()
                                        + po().Detail(lnCtr).InvStockRequestDetail().getCancelled());
                                if (!po().Detail(lnCtr).getSouceNo().isEmpty()) {
                                    if (lnRequestQuantity != lnTotalQty) {
                                        status = "1";
                                    }
                                }
                                break;
                            case PurchaseOrderStatus.SourceCode.POQUOTATION:
                                lnRequestQuantity = po().Detail(lnCtr).POQuotationDetail().getQuantity();
                                break;
                        }
                        detailsList.add(new ModelPurchaseOrderDetail(
                                String.valueOf(lnCtr + 1),
                                orderDetail.getSouceNo(),
                                orderDetail.Inventory().getBarCode(),
                                orderDetail.Inventory().getDescription(),
                                orderDetail.getDescription(),
                                CustomCommonUtil.setIntegerValueToDecimalFormat(orderDetail.getUnitPrice(), true),
                                "0",
                                CustomCommonUtil.setDecimalValueToIntegerFormat(lnRequestQuantity),
                                CustomCommonUtil.setDecimalValueToIntegerFormat(orderDetail.getQuantity().doubleValue()),
                                CustomCommonUtil.setIntegerValueToDecimalFormat(lnTotalAmount, true),
                                status
                        ));
                    }
                    final double totalAmountFinal = grandTotalAmount;
                    Platform.runLater(() -> {
                        detail_data.setAll(detailsList); // Properly update list
                        tblVwOrderDetails.setItems(detail_data);
                        if (pnEditMode == EditMode.ADDNEW || pnEditMode == EditMode.UPDATE) {
                            if (totalAmountFinal <= 0.0000) {
                                tfDiscountRate.setText("0.00");
                                tfAdvancePRate.setText("0.00");
                                tfDiscountAmount.setText("0.0000");
                                tfAdvancePAmount.setText("0.0000");
                                po().Master().setAdditionalDiscount(0.0000);
                                po().Master().setDiscount(0.00);
                                po().Master().setDownPaymentRatesAmount(0.0000);
                                po().Master().setDownPaymentRatesPercentage(0.00);
                            }
                            po().Master().setTranTotal(totalAmountFinal);
                            po().computeNetTotal();
                            tfDiscountRate.setText(CustomCommonUtil.setIntegerValueToDecimalFormat(po().Master().getDiscount().doubleValue()));
                            tfAdvancePRate.setText(CustomCommonUtil.setIntegerValueToDecimalFormat(po().Master().getDownPaymentRatesPercentage().doubleValue()));
                            tfTotalAmount.setText(CustomCommonUtil.setIntegerValueToDecimalFormat(po().Master().getTranTotal(), true
                            ));
                            tfNetAmount.setText(CustomCommonUtil.setIntegerValueToDecimalFormat(po().Master().getNetTotal(), true));
                        }
                        reselectLastDetailRow();
                        initFields(pnEditMode);
                    });

                    return detailsList;

                } catch (GuanzonException | SQLException ex) {
                    Logger.getLogger(PurchaseOrderMP_PreviewController.class
                            .getName()).log(Level.SEVERE, null, ex);
                    return null;
                }
            }

            @Override
            protected void succeeded() {
                progressIndicator.setVisible(false);
            }

            @Override
            protected void failed() {
                progressIndicator.setVisible(false);
            }
        };

        //Runs on the JavaFX thread: the PO detail models (and their lazily
        //loaded Inventory models) are not thread-safe, and the screen reads
        //them at the same time (initFields / loadRecordDetail).
        task.run();
    }

    private void initTableDetail() {
        JFXUtil.setColumnCenter(tblRowNoDetail, tblOrderNoDetail);
        JFXUtil.setColumnLeft(tblBarcodeDetail, tblDescriptionDetail, tblAdditionalDescDetail);
        JFXUtil.setColumnRight(tblCostDetail, tblRequestQuantityDetail, tblOrderQuantityDetail, tblTotalAmountDetail, tblROQDetail);
        JFXUtil.setColumnsIndexAndDisableReordering(tblVwOrderDetails);

        initTableHighlithers();
    }

    private void reselectLastDetailRow() {
        if (pnTblDetailRow >= 0 && pnTblDetailRow < tblVwOrderDetails.getItems().size()) {
            tblVwOrderDetails.getSelectionModel().clearAndSelect(pnTblDetailRow);
            tblVwOrderDetails.getSelectionModel().focus(pnTblDetailRow); // Scroll to the selected row if needed
        }
    }

    private void initTextFieldsProperty() {
        tfSupplier.textProperty().addListener((observable, oldValue, newValue) -> {
                    if (pbShowingSupplier || !isEditing()) {
                        return;
                    }
                    if (newValue != null) {
                        if (newValue.isEmpty()) {
                            try {
                                if (!isExchangingSupplier()) {
                                    return;
                                }
                                po().Master().setSupplierID("");
                                po().Master().setAddressID("");
                                po().Master().setContactID("");
                                tfSupplier.setText("");
                                prevSupplier = "";
                                main_data.clear();
                                clearStockRequestTable();
                                if (pnEditMode == EditMode.ADDNEW || pnEditMode == EditMode.UPDATE) {
                                    po().resetTerm();
                                    tfTerm.setText(termText());
                                }
                            } catch (GuanzonException | SQLException ex) {
                                Logger.getLogger(PurchaseOrderMP_PreviewController.class
                                        .getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                    }
                }
        );
    }

    // =====================================================================
    // Searchable fields. One method per field, used by the Search button,
    // Enter / F3 (and F4 on items) in the field, and the auto-suggest pick,
    // so all of them update the screen the same way.
    // =====================================================================
    private void initAutoSuggest() {
        AutoSuggest.attach(tfSupplier, text -> po().Suggestions().supplier(text), this::pickSupplier);
        AutoSuggest.attach(tfDestination, text -> po().Suggestions().destination(text), this::pickDestination);
        AutoSuggest.attach(tfTerm, text -> po().Suggestions().term(text), this::pickTerm);
//        AutoSuggest.attach(tfReferenceNo, text -> po().Suggestions().project(text), this::pickProject);
        //General Inventory: the PO's supplier, any industry (as Enter / F3)
        AutoSuggest.attach(tfBarcode, text -> po().Suggestions().barcode(text, pnTblDetailRow, true, false),
                picked -> pickItem(picked, tfBarcode, true));
        AutoSuggest.attach(tfDescription, text -> po().Suggestions().description(text, pnTblDetailRow, true, false),
                picked -> pickItem(picked, tfDescription, false));
    }

    /**
     * Search (KwikSearch) of a searchable field with the typed value.
     *
     * @return false when the user kept the current supplier (nothing searched)
     */
    private boolean searchField(String fsFieldId, String fsValue)
            throws SQLException, GuanzonException, CloneNotSupportedException {
        switch (fsFieldId) {
//            case "tfReferenceNo":
//                showProject(po().SearchProject(fsValue));
//                return true;
            case "tfSupplier":
                if (!isExchangingSupplier()) {
                    return false;
                }
                showSupplier(po().SearchSupplier(fsValue, false));
                return true;
            case "tfDestination":
                showDestination(po().SearchDestination(fsValue, false));
                return true;
            case "tfTerm":
                showTerm(po().SearchTerm(fsValue, false));
                return true;
            case "tfBarcode":
                searchItem(tfBarcode, fsValue, true, true);
                return true;
            case "tfDescription":
                searchItem(tfDescription, fsValue, false, true);
                return true;
            default:
                return true;
        }
    }

    /**
     * Item search of the current row: Barcode searches by barcode, Description
     * by description (Enter in Barcode used to search the description column).
     *
     * @param fbBySupplier only the PO supplier's items (Enter / F3); false for F4
     */
    private void searchItem(TextField foField, String fsValue, boolean fbBarcode, boolean fbBySupplier)
            throws SQLException, GuanzonException, CloneNotSupportedException {
        if (!isDetailRowSelected()) {
            return;
        }
        showItem(fbBarcode
                ? po().SearchBarcode(fsValue, true, pnTblDetailRow, fbBySupplier)
                : po().SearchBarcodeDescription(fsValue, false, pnTblDetailRow, fbBySupplier), foField);
    }

    private void pickSupplier(Suggestion foPicked) {
        try {
            if (isExchangingSupplier() && showSupplier(po().SelectSupplier(foPicked.getId()))) {
                CommonUtils.SetNextFocus(tfSupplier);
            }
        } catch (SQLException | GuanzonException ex) {
            Logger.getLogger(PurchaseOrderMP_PreviewController.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void pickDestination(Suggestion foPicked) {
        try {
            if (showDestination(po().SelectDestination(foPicked.getId()))) {
                CommonUtils.SetNextFocus(tfDestination);
            }
        } catch (SQLException | GuanzonException ex) {
            Logger.getLogger(PurchaseOrderMP_PreviewController.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void pickTerm(Suggestion foPicked) {
        try {
            if (showTerm(po().SelectTerm(foPicked.getId()))) {
                CommonUtils.SetNextFocus(tfTerm);
            }
        } catch (SQLException | GuanzonException ex) {
            Logger.getLogger(PurchaseOrderMP_PreviewController.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
//
//    private void pickProject(Suggestion foPicked) {
//        try {
//            if (showProject(po().SelectProject(foPicked.getId()))) {
//                CommonUtils.SetNextFocus(tfReferenceNo);
//            }
//        } catch (SQLException | GuanzonException ex) {
//            Logger.getLogger(PurchaseOrderMP_EntryController.class.getName()).log(Level.SEVERE, null, ex);
//        }
//    }

    /** Item picked in the barcode or description field of the current row. */
    private void pickItem(Suggestion foPicked, TextField foField, boolean fbBarcode) {
        try {
            if (!isDetailRowSelected()) {
                return;
            }
            showItem(fbBarcode
                    ? po().SelectBarcode(foPicked.getId(), pnTblDetailRow)
                    : po().SelectBarcodeDescription(foPicked.getId(), pnTblDetailRow), foField);
        } catch (SQLException | GuanzonException ex) {
            Logger.getLogger(PurchaseOrderMP_PreviewController.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    // ---- screen updates after a search or a pick (true: record loaded) ----
    private boolean isFound(JSONObject foResult, TextField foField) {
        if ("error".equals(foResult.get("result"))) {
            ShowMessageFX.Warning((String) foResult.get("message"), psFormName, null);
            foField.setText("");
            return false;
        }
        return true;
    }

    private boolean showSupplier(JSONObject foResult) throws SQLException, GuanzonException {
        if (!isFound(foResult, tfSupplier)) {
            return false;
        }
        prevSupplier = po().Master().getSupplierID();
        tfSupplier.setText(po().Master().Supplier().getCompanyName());
        if (!tfSupplier.getText().isEmpty()) {
            loadTableMain();
        }
        selectTheExistedDetailFromMainTable();
        return true;
    }

    private boolean showDestination(JSONObject foResult) throws SQLException, GuanzonException {
        if (!isFound(foResult, tfDestination)) {
            return false;
        }
        tfDestination.setText(po().Master().Branch().getBranchName());
        return true;
    }

    private boolean showTerm(JSONObject foResult) throws SQLException, GuanzonException {
        if (!isFound(foResult, tfTerm)) {
            return false;
        }
        tfTerm.setText(termText());
        selectTheExistedDetailFromMainTable();
        return true;
    }

    private boolean showProject(JSONObject foResult) {
        if (!isFound(foResult, tfReferenceNo)) {
            return false;
        }
        tfReferenceNo.setText(po().Master().getReference());
        return true;
    }

    /** Item search / pick result; a duplicate item moves to its row ("tableRow"). */
    private void showItem(JSONObject foResult, TextField foField) {
        if ("error".equals(foResult.get("result"))) {
            ShowMessageFX.Warning((String) foResult.get("message"), psFormName, null);
            foField.setText("");
            if (foResult.get("tableRow") == null) {
                return;
            }
            pnTblDetailRow = (int) foResult.get("tableRow");
        }
        loadTableDetail();
        loadRecordDetail();
        initDetailFocus();
        selectTheExistedDetailFromMainTable();
    }

    private boolean isDetailRowSelected() {
        if (pnTblDetailRow < 0) {
            ShowMessageFX.Warning("Invalid row to update.", psFormName, null);
            clearDetailFields();
            return false;
        }
        return true;
    }

    private boolean isExchangingSupplier() {
        if (pnEditMode == EditMode.ADDNEW || pnEditMode == EditMode.UPDATE) {
            boolean isHaveQuantityAndStockId = false;
            if (po().getDetailCount() >= 1) {
                if (po().Detail(0).getStockID() != null && po().Detail(0).getQuantity() != null) {
                    if (!po().Detail(0).getStockID().isEmpty()
                            || po().Detail(0).getQuantity().intValue() != 0) {
                        isHaveQuantityAndStockId = true;
                    }
                }
            }
            if (isHaveQuantityAndStockId) {
                if (ShowMessageFX.YesNo("PO Details have already items, are you sure you want to change supplier?", psFormName, null)) {
                    try {
                        int detailCount = po().getDetailCount();
                        for (int lnCtr = detailCount - 1; lnCtr >= 0; lnCtr--) {
                            if (po().Detail(lnCtr).getSouceNo().isEmpty()
                                    && po().Detail(lnCtr).getStockID().isEmpty()
                                    && po().Detail(lnCtr).getQuantity().intValue() == 0) {
                                continue; // Skip deleting this row
                            }
                            po().RemoveDetail(lnCtr); //saved rows are kept for the stock request update
                        }
                        pnTblDetailRow = 0;
                        pnTblMainRow = -1;
                        if (tblVwStockRequest != null) {
                            tblVwStockRequest.getSelectionModel().clearSelection();
                        }
                        po().resetTerm();
                        tfTerm.setText(termText());
                        clearDetailFields();
                        loadTableDetail();
                    } catch (GuanzonException | SQLException | CloneNotSupportedException ex) {
                        Logger.getLogger(PurchaseOrderMP_PreviewController.class.getName()).log(Level.SEVERE, null, ex);
                    }
                } else {
                    if (po().Master().getSupplierID().isEmpty()) {
                        return false;
                    } else {
                        try {
                            poJSON = new JSONObject();
                            //reload the current supplier by its id (no search dialog)
                            poJSON = po().SelectSupplier(po().Master().getSupplierID());
                            if (!"success".equals((String) poJSON.get("result"))) {
                                ShowMessageFX.Warning((String) poJSON.get("message"), psFormName, null);
                                return false;
                            }
                            tfSupplier.setText(po().Master().Supplier().getCompanyName());
                            //change declined: the supplier stays, so its term stays too (it was reset here)
                            tfTerm.setText(termText());
                            selectTheExistedDetailFromMainTable();
                            return false;

                        } catch (ExceptionInInitializerError | SQLException | GuanzonException ex) {
                            Logger.getLogger(PurchaseOrderMP_PreviewController.class
                                    .getName()).log(Level.SEVERE, null, ex);
                        }
                    }

                }
            }
        }
        if (pnEditMode == EditMode.READY) {
            try {
                if (!tfTransactionNo.getText().isEmpty()
                        && !tfDestination.getText().isEmpty()) {
                    if (ShowMessageFX.YesNo("You have an open transaction. Are you sure you want to change the supplier?", psFormName, null)) {
                        clearDetailFields();
                        clearMasterFields();
                        detail_data.clear();
                        tblVwOrderDetails.getItems().clear();
                        pnEditMode = EditMode.UNKNOWN;
                        po().Master().setSupplierID(prevSupplier);
                        tfSupplier.setText(po().Master().Supplier().getCompanyName());
                        pnTblMainRow = -1;
                        clearStockRequestTable();
                        initButtons(pnEditMode);
                        initFields(pnEditMode);
                        return true;
                    } else {
                        poJSON = new JSONObject();
                        //reload the current supplier by its id (no search dialog)
                        poJSON = po().SelectSupplier(po().Master().getSupplierID());
                        if (!"success".equals((String) poJSON.get("result"))) {
                            ShowMessageFX.Warning((String) poJSON.get("message"), psFormName, null);
                            return false;
                        }
                        tfSupplier.setText(po().Master().Supplier().getCompanyName());
                        selectTheExistedDetailFromMainTable();
                        return false;

                    }
                }
            } catch (ExceptionInInitializerError | SQLException | GuanzonException ex) {
                Logger.getLogger(PurchaseOrderMP_PreviewController.class
                        .getName()).log(Level.SEVERE, null, ex);
            }
        }

        return true;
    }

    private void tblVwMain_Clicked(MouseEvent event) {
        if (pnEditMode == EditMode.ADDNEW || pnEditMode == EditMode.UPDATE) {
            pnTblMainRow = tblVwStockRequest.getSelectionModel().getSelectedIndex();
            if (event.getClickCount() == 2) {
                if (!tfSupplier.getText().isEmpty()) {
                    ModelPurchaseOrder loSelectedStockRequest = (ModelPurchaseOrder) tblVwStockRequest.getSelectionModel().getSelectedItem();
                    if (loSelectedStockRequest != null) {
                        String lsTransactionNo = loSelectedStockRequest.getIndex06();
                        String lsSource = loSelectedStockRequest.getIndex08();
                        try {
                            switch (lsSource) {
                                case PurchaseOrderStatus.SourceCode.STOCKREQUEST:
                                    poJSON = po().addStockRequestOrdersToPODetail(lsTransactionNo);
                                    break;
                                case PurchaseOrderStatus.SourceCode.POQUOTATION:
                                    poJSON = po().addPOQuotationToPODetail(lsTransactionNo);
                                    break;
                            }
                            if ("success".equals(poJSON.get("result"))) {
                                if (po().getDetailCount() > 0) {
                                    pnTblDetailRow = po().getDetailCount() - 1;
                                    loadTableDetailAndSelectedRow();
                                    main_data.get(pnTblMainRow).setIndex07(PurchaseOrderStatus.CONFIRMED);
                                    selectTheExistedDetailFromMainTable();
                                }
                            } else {
                                ShowMessageFX.Warning((String) poJSON.get("message"), psFormName, null);

                            }
                        } catch (CloneNotSupportedException | SQLException | GuanzonException ex) {
                            Logger.getLogger(PurchaseOrderMP_PreviewController.class
                                    .getName()).log(Level.SEVERE, null, ex);
                            ShowMessageFX.Warning("Error loading data: " + ex.getMessage(), psFormName, null);
                        }
                    }
                } else {
                    ShowMessageFX.Warning("Can't allow to add this transaction if the company or supplier is empty.", psFormName, null);
                }
            }
        }
    }

    private void loadTableDetailAndSelectedRow() {
        if (pnTblDetailRow >= 0) {
            Platform.runLater(() -> {
                // Run a delay after the UI thread is free
                PauseTransition delay = new PauseTransition(Duration.millis(10));
                delay.setOnFinished(event -> {
                    Platform.runLater(() -> { // Run UI updates in the next cycle
                        loadTableDetail();
                    });
                });
                delay.play();
            });
            loadRecordDetail();
            initDetailFocus();
        }
    }

    private void selectTheExistedDetailFromMainTable() {
        if (!main_data.isEmpty()) {
            Set<String> existingDetailIds = detail_data.stream()
                    .map(ModelPurchaseOrderDetail::getIndex02)
                    .collect(Collectors.toSet());

            if (pnEditMode != EditMode.ADDNEW) {
                for (ModelPurchaseOrder master : main_data) {
                    master.setIndex07(existingDetailIds.contains(master.getIndex06()) ? PurchaseOrderStatus.CONFIRMED : PurchaseOrderStatus.OPEN);
                }
            }

            if (tblVwStockRequest != null) {
                tblVwStockRequest.refresh();
            }
        }
    }

    private void tblVwDetail_Clicked(MouseEvent event) {
        if (pnEditMode == EditMode.ADDNEW || pnEditMode == EditMode.UPDATE || pnEditMode == EditMode.READY) {
            pnTblDetailRow = tblVwOrderDetails.getSelectionModel().getSelectedIndex();
            ModelPurchaseOrderDetail selectedItem = tblVwOrderDetails.getSelectionModel().getSelectedItem();
            if (event.getClickCount() == 1) {
                clearDetailFields();
                if (selectedItem != null) {
                    if (pnTblDetailRow >= 0) {
                        loadRecordDetail();
                        initDetailFocus();
                    }
                }
                initFields(pnEditMode);
            }
        }
    }

    /** Inventory type of an item row; empty for the row without an item (no lookup). */
    private String inventoryTypeOf(int fnRow) throws GuanzonException, SQLException {
        String lsStockId = po().Detail(fnRow).getStockID();
        if (lsStockId == null || lsStockId.isEmpty()) {
            return "";
        }
        String lsType = po().Detail(fnRow).Inventory().getInventoryTypeId();
        return lsType == null ? "" : lsType;
    }

    /** Row menu of the item table: Remove item. */
    private void initDetailContextMenu() {
        MenuItem loRemove = new MenuItem("Remove item (Delete)");
        loRemove.setOnAction(event -> removeSelectedDetail());
        ContextMenu loMenu = new ContextMenu(loRemove);
        loMenu.setOnShowing(event -> loRemove.setDisable(pnEditMode != EditMode.ADDNEW && pnEditMode != EditMode.UPDATE));
        tblVwOrderDetails.setContextMenu(loMenu);
    }

    /**
     * Removes the selected item from the PO (controller RemoveDetail): a saved
     * line is kept by the controller so its stock request / quotation is
     * released on save.
     */
    private void removeSelectedDetail() {
        if (pnEditMode != EditMode.ADDNEW && pnEditMode != EditMode.UPDATE) {
            return;
        }
        int lnRow = tblVwOrderDetails.getSelectionModel().getSelectedIndex();
        if (lnRow < 0 || lnRow >= po().getDetailCount()) {
            return;
        }
        String lsStockId = po().Detail(lnRow).getStockID();
        if (lsStockId == null || lsStockId.isEmpty()) {
            return; //the empty row for typing
        }
        if (!ShowMessageFX.YesNo(null, psFormName, "Remove this item from the order?")) {
            return;
        }
        try {
            poJSON = po().RemoveDetail(lnRow);
        } catch (CloneNotSupportedException ex) {
            Logger.getLogger(PurchaseOrderMP_PreviewController.class.getName()).log(Level.SEVERE, null, ex);
            return;
        }
        if (!"success".equals(poJSON.get("result"))) {
            ShowMessageFX.Warning((String) poJSON.get("message"), psFormName, null);
            return;
        }
        pnTblDetailRow = Math.min(lnRow, po().getDetailCount() - 1);
        clearDetailFields();
        loadTableDetail();
        loadRecordDetail();
        selectTheExistedDetailFromMainTable();
        initFields(pnEditMode);
    }

    /** A new PO focuses Supplier until the user leaves it (the item row loads later and would take the focus). */
    private boolean pbFocusHeader;

    private void focusDetail(TextField foField) {
        if (pbFocusHeader) {
            tfSupplier.requestFocus();
        } else {
            foField.requestFocus();
        }
    }

    private void initDetailFocus() {
        if (pnEditMode == EditMode.ADDNEW || pnEditMode == EditMode.UPDATE) {
            if (pnTblDetailRow >= 0) {
                try {
                    boolean isSourceNotEmpty = !po().Detail(pnTblDetailRow).getSouceNo().isEmpty();
                    tfBarcode.setDisable(isSourceNotEmpty);
                    tfDescription.setDisable(isSourceNotEmpty);

                    if (PurchaseOrderStaticData.INVENTORY_TYPE_FREE_ENTRY.equals(inventoryTypeOf(pnTblDetailRow))) {
                        CustomCommonUtil.setDisable(false, tfBarcode, tfDescription);
                        focusDetail(tfOrderQuantity);
                    }
                    if (isSourceNotEmpty && !tfBarcode.getText().isEmpty()) {
                        focusDetail(tfOrderQuantity);
                    } else {
                        if (!tfBarcode.getText().isEmpty() && (pnEditMode == EditMode.UPDATE || pnEditMode == EditMode.ADDNEW)) {
                            focusDetail(tfOrderQuantity);
                        } else {
                            focusDetail(tfBarcode);
                        }
                    }
                } catch (GuanzonException | SQLException ex) {
                    Logger.getLogger(PurchaseOrderMP_PreviewController.class.getName()).log(Level.SEVERE, null, ex);
                }
            }

        }
    }



    private void initStackPaneListener() {
        stackPane1.widthProperty().addListener((observable, oldValue, newWidth) -> {
            double computedWidth = newWidth.doubleValue();
            ldstackPaneWidth = computedWidth;

        });
        stackPane1.heightProperty().addListener((observable, oldValue, newHeight) -> {
            double computedHeight = newHeight.doubleValue();
            ldstackPaneHeight = computedHeight;
            loadTableAttachment();
            loadRecordAttachment(true);
            initAttachmentsGrid();
        });
    }

    private void initAttachmentPreviewPane() {
        stackPane1.layoutBoundsProperty().addListener((observable, oldBounds, newBounds) -> {
            stackPane1.setClip(new javafx.scene.shape.Rectangle(
                    newBounds.getMinX(),
                    newBounds.getMinY(),
                    newBounds.getWidth(),
                    newBounds.getHeight()
            ));
        });
        imageView.setOnScroll((ScrollEvent event) -> {
            double delta = event.getDeltaY();
            scaleFactor = Math.max(0.5, Math.min(scaleFactor * (delta > 0 ? 1.1 : 0.9), 5.0));
            imageView.setScaleX(scaleFactor);
            imageView.setScaleY(scaleFactor);
        });

        imageView.setOnMousePressed((MouseEvent event) -> {
            mouseAnchorX = event.getSceneX() - imageView.getTranslateX();
            mouseAnchorY = event.getSceneY() - imageView.getTranslateY();
        });

        imageView.setOnMouseDragged((MouseEvent event) -> {
            double translateX = event.getSceneX() - mouseAnchorX;
            double translateY = event.getSceneY() - mouseAnchorY;
            imageView.setTranslateX(translateX);
            imageView.setTranslateY(translateY);
        });

        stackPane1.widthProperty().addListener((observable, oldValue, newWidth) -> {
            double computedWidth = newWidth.doubleValue();
            ldstackPaneWidth = computedWidth;

        });
        stackPane1.heightProperty().addListener((observable, oldValue, newHeight) -> {
            double computedHeight = newHeight.doubleValue();
            ldstackPaneHeight = computedHeight;

            //Placed to get height and width of stack pane in computed size before loading the image
            initStackPaneListener();
            initAttachmentsGrid();
        });

    }

    private void initAttachmentsGrid() {
        /*FOCUS ON FIRST ROW*/
        JFXUtil.setColumnCenter(tblRowNoAttachment);
        JFXUtil.setColumnLeft(tblFileNameAttachment);
        JFXUtil.setColumnsIndexAndDisableReordering(tblAttachments);
        tblAttachments.setItems(attachment_data);

        if (pnAttachment < 0 || pnAttachment >= attachment_data.size()) {
            if (!attachment_data.isEmpty()) {
                /* FOCUS ON FIRST ROW */
                tblAttachments.getSelectionModel().select(0);
                tblAttachments.getFocusModel().focus(0);
                pnAttachment = tblAttachments.getSelectionModel().getSelectedIndex();
            }
        } else {
            /* FOCUS ON THE ROW THAT pnRowDetail POINTS TO */
            tblAttachments.getSelectionModel().select(pnAttachment);
            tblAttachments.getFocusModel().focus(pnAttachment);
        }

    }

    private void slideImage(int direction) {
        if (attachment_data.size() <= 0) {
            return;
        }

        currentIndex = pnAttachment;
        int newIndex = currentIndex + direction;

        if (newIndex != -1 && (newIndex <= attachment_data.size() - 1)) {
            ModelPOAttachment image = attachment_data.get(newIndex);
            String filePath2 = "D:\\GGC_Maven_Systems\\temp\\attachments\\" + image.getIndex02();
            TranslateTransition slideOut = new TranslateTransition(Duration.millis(300), imageView);
            slideOut.setByX(direction * -400); // Move left or right

            tblAttachments.getFocusModel().focus(newIndex);
            tblAttachments.getSelectionModel().select(newIndex);
            pnAttachment = newIndex;
            loadRecordAttachment(false);

            // Create a transition animation
            slideOut.setOnFinished(event -> {
                imageView.setTranslateX(direction * 400);
                TranslateTransition slideIn = new TranslateTransition(Duration.millis(300), imageView);
                slideIn.setToX(0);
                slideIn.play();

                loadRecordAttachment(true);
            });

            slideOut.play();
        }
        if (isImageViewOutOfBounds(imageView, stackPane1)) {
            resetImageBounds();
        }
    }

    private boolean isImageViewOutOfBounds(ImageView imageView, StackPane stackPane) {
        Bounds clipBounds = stackPane.getClip().getBoundsInParent();
        Bounds imageBounds = imageView.getBoundsInParent();

        return imageBounds.getMaxX() < clipBounds.getMinX()
                || imageBounds.getMinX() > clipBounds.getMaxX()
                || imageBounds.getMaxY() < clipBounds.getMinY()
                || imageBounds.getMinY() > clipBounds.getMaxY();
    }

    private void resetImageBounds() {
        imageView.setScaleX(1.0);
        imageView.setScaleY(1.0);
        imageView.setTranslateX(0);
        imageView.setTranslateY(0);
        stackPane1.setAlignment(imageView, Pos.CENTER);
    }

    private void stackPaneClip() {
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(
                stackPane1.getWidth() - 8, // Subtract 10 for padding (5 on each side)
                stackPane1.getHeight() - 8 // Subtract 10 for padding (5 on each side)
        );
        clip.setArcWidth(8); // Optional: Rounded corners for aesthetics
        clip.setArcHeight(8);
        clip.setLayoutX(4); // Set padding offset for X
        clip.setLayoutY(4); // Set padding offset for Y
        stackPane1.setClip(clip);

    }
    private void initComboBoxes() {
        JFXUtil.initComboBoxCellDesignColor("#FF8201", cmbOrderType);
        cmbOrderType.setItems(PurchaseOrderStaticData.ORDER_TYPE_DESCRIPTION);
        cmbOrderType.setOnAction(event -> {
            int lnIndex = cmbOrderType.getSelectionModel().getSelectedIndex();
            if (pbShowingOrderType || !isEditing() || lnIndex < 0) {
                return;
            }
            //the value saved is the selected index
            poJSON = po().setOrderType(String.valueOf(lnIndex));
            if (!"success".equals(poJSON.get("result"))) {
                ShowMessageFX.Warning((String) poJSON.get("message"), psFormName, null);
                loadRecordOrderType();
            }
        });

        JFXUtil.initComboBoxCellDesignColor("#FF8201", cmbAttachmentType);
        cmbAttachmentType.setItems(documentType);
        cmbAttachmentType.setOnAction(event -> {
            if (attachment_data.size() > 0) {
                int selectedIndex = cmbAttachmentType.getSelectionModel().getSelectedIndex();
                po().TransactionAttachmentList(pnAttachment).getModel().setDocumentType("000" + String.valueOf(selectedIndex));
                cmbAttachmentType.getSelectionModel().select(selectedIndex);
            }
        });
    }
}