package ph.com.guanzongroup.cas.po.ui;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.guanzon.appdriver.agent.ShowMessageFX;
import org.guanzon.appdriver.base.*;
import org.json.simple.JSONObject;
import ph.com.guanzongroup.cas.po.controller.PurchaseOrderMP;
import ph.com.guanzongroup.cas.po.services.PurchaseOrderControllers;
import ph.com.guanzongroup.cas.po.status.POStatus;
import ph.com.guanzongroup.cas.po.status.PurchaseOrderStaticData;
import ph.com.guanzongroup.cas.po.status.PurchaseOrderStatus;
import ph.com.guanzongroup.cas.po.ui.utility.*;

import java.io.IOException;
import java.net.URL;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Landing screen of the Purchase Order MP module (PurchaseOrderLanding.fxml).
 *
 * <p>
 * Lists the purchase orders of the current industry / company / category with
 * filters (search, date range, order type, status) and paging, and runs the
 * status actions on the selected row: Update (hands over to the entry screen),
 * Confirm, Approve, Return, Void, Cancel, Print, Export and Status History.
 * Based on {@link PurchaseOrderMP_EntryController}: the same
 * {@link PurchaseOrderMP} controller, messages, and status rules.</p>
 *
 * <p>
 * The controller is filled by the launcher through the {@code set...}
 * methods. The purchase order controller is created after the screen is shown
 * (not inside {@code initialize}), so the industry / company / category set by
 * the launcher after loading the FXML are already known.</p>
 */
public class PurchaseOrderMP_LandingController implements Initializable, ScreenInterface {

    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");

    private GRiderCAS poApp;
    private PurchaseOrderControllers poPurchasingController;
    private PurchaseOrderMP poMP;
    private JSONObject poJSON;

    private final String psFormName = PurchaseOrderStaticData.FORM_NAME_PURCHASE_ORDER;
    private LogWrapper logWrapper;

    private String psIndustryID = "";
    private String psCompanyID = "";
    private String psCategoryID = "";

    /** What the Close button does (e.g. close the tab); default: close the window. */
    private Runnable poOnClose;
    /** What the Update button does: opens the entry screen on the given transaction no. */
    private Consumer<String> poOnUpdate;

    /** Every transaction retrieved, and the part of it matching the filters. */
    private final List<Row> paAllRows = new ArrayList<>();
    private final ObservableList<Row> paFiltered = FXCollections.observableArrayList();
    private final PauseTransition poSearchDelay = new PauseTransition(Duration.millis(300));

    private int pnPage = 0;
    private int pnPageSize = PurchaseOrderStaticData.FILTER_DEFAULT_PAGE_SIZE;
    private boolean pbStarted;
    /**
     * OpenTransaction loads a PO into Master(), replacing the industry, company and
     * category the list is filtered by. When set, the controller is created again
     * before the next retrieve.
     */
    private boolean pbContextDirty;
    private boolean pbResettingFilters;

    // ---- screen controls (fx:id of PurchaseOrderLanding.fxml) ----
    @FXML
    private AnchorPane ChildAnchorPane, apSearchMaster;
    @FXML
    private VBox vbMain;
    @FXML
    private HBox hbButtons;

    @FXML
    private TextField tfSearch;
    @FXML
    private DatePicker dpDateFrom, dpDateTo;
    /** Order type filter (the combo labelled "Order Type"). */
    @FXML
    private ComboBox<String> cmbPaymentFilter;
    /** Status filter (the combo next to the "Status" label). */
    @FXML
    private ComboBox<String> cmbStatus;
    @FXML
    private Button btnClearFilter;

    @FXML
    private Label lblRecordInfo, lblPage;
    /** Summary cards: amount and number of transactions per group. */
    @FXML
    private Label lblTotalAmount, lblTotalCount, lblPendingAmount, lblPendingCount,
            lblApprovedAmount, lblApprovedCount, lblVoidedAmount, lblVoidedCount;
    @FXML
    private TableView<Row> tblTransactions;
    @FXML
    private TableColumn<Row, String> tblRowNo, tblRowTransNo, tblRowDate, tblRowCustomer,
            tblRowTransNo1, tblRowSource, tblRowTotal, tblRowStatus;

    @FXML
    private ComboBox<Integer> cmbPageSize;
    @FXML
    private Button btnPageFirst, btnPagePrev, btnPageNext, btnPageLast;

    @FXML
    private Button btnRefresh, btnUpdate, btnSave, btnCancel, btnSave1, btnSave11,
            btnReturn, btnVoid, btnPrint, btnExport, btnVoid1, btnClose;
    // btnSave1 = Confirm, btnSave11 = Approve, btnVoid1 = History (names kept from the FXML)

    // =====================================================================
    // ScreenInterface
    // =====================================================================
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

    @Override
    public void setOnClose(Runnable foOnClose) {
        poOnClose = foOnClose;
    }

    /** What Update does with the selected transaction no. (e.g. open it in the entry screen). */
    public void setOnUpdate(Consumer<String> foOnUpdate) {
        poOnUpdate = foOnUpdate;
    }

    /** Nothing is edited on this screen. */
    @Override
    public boolean hasUnsavedChanges() {
        return false;
    }

    private PurchaseOrderMP po() {
        return poMP;
    }

    // =====================================================================
    // Start up
    // =====================================================================
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        initButtons();
        initFilters();
        initTable();
        initPaging();
        updateButtons();

        //the launcher sets the industry / company / category after loading the FXML
        Platform.runLater(this::start);
    }

    private void start() {
        if (pbStarted) {
            return;
        }
        pbStarted = true;
        if (!initPurchaseOrder()) {
            ShowMessageFX.Warning("Unable to initialize Purchase Order controller. "
                    + "Please check the application log.", psFormName, null);
            return;
        }
        loadTransactions();
    }

    /** Creates the MP purchase order controller through the service. */
    private boolean initPurchaseOrder() {
        try {
            poPurchasingController = new PurchaseOrderControllers(poApp, logWrapper);

            Object loPO = poPurchasingController.PurchaseOrder(psIndustryID);
            if (!(loPO instanceof PurchaseOrderMP)) {
                throw new IllegalStateException("Invalid Purchase Order controller for industry: " + psIndustryID);
            }
            poMP = (PurchaseOrderMP) loPO;

            poJSON = poMP.InitTransaction();
            if (poJSON == null || !"success".equals(poJSON.get("result"))) {
                Logger.getLogger(getClass().getName()).severe(poJSON == null
                        ? "Unable to initialize Purchase Order." : String.valueOf(poJSON.get("message")));
                return false;
            }

            poMP.Master().setIndustryID(psIndustryID);
            poMP.Master().setCategoryCode(psCategoryID);
            if (psCompanyID == null || psCompanyID.trim().isEmpty()) {
                poMP.Master().setCompanyID(poApp.getCompnyId());
            } else {
                poMP.Master().setCompanyID(psCompanyID);
            }
            return true;
        } catch (Exception ex) {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Purchase Order initialization failed.", ex);
            return false;
        }
    }

    // =====================================================================
    // Controls
    // =====================================================================
    private void initButtons() {
        List<Button> loButtons = Arrays.asList(btnRefresh, btnUpdate, btnSave1, btnSave11, btnReturn,
                btnVoid, btnCancel, btnPrint, btnExport, btnVoid1, btnClose, btnClearFilter);
        loButtons.forEach(button -> button.setOnAction(this::handleButtonAction));

        //nothing is typed or saved on the landing screen
        btnSave.setVisible(false);
        btnSave.setManaged(false);
    }

    private void handleButtonAction(ActionEvent event) {
        String lsButton = ((Button) event.getSource()).getId();
        try {
            switch (lsButton) {
                case "btnRefresh":
                    loadTransactions();
                    break;
                case "btnClearFilter":
                    clearFilters();
                    break;
                case "btnUpdate":
                    updateSelected();
                    break;
                case "btnSave1": //Confirm
                    runStatusAction("Confirm", "Are you sure you want to confirm this transaction?", false,
                            remarks -> po().ConfirmTransaction(remarks));
                    break;
                case "btnSave11": //Approve
                    runStatusAction("Approve", "Are you sure you want to approve this transaction?", false,
                            remarks -> po().ApproveTransaction(remarks));
                    break;
                case "btnReturn":
                    runStatusAction("Return", "Are you sure you want to return this transaction?", true,
                            remarks -> po().ReturnTransaction(remarks));
                    break;
                case "btnVoid":
                    runStatusAction("Void", "Are you sure you want to void this transaction?", true,
                            remarks -> po().VoidTransaction(remarks));
                    break;
                case "btnCancel": //cancels a Confirmed / Approved purchase order
                    runStatusAction("Cancel", "Are you sure you want to cancel this transaction?", true,
                            remarks -> po().CancelTransaction(remarks));
                    break;
                case "btnPrint":
                    printSelected();
                    break;
                case "btnExport":
                    exportSelected();
                    break;
                case "btnVoid1": //History
                    showHistory();
                    break;
                case "btnClose":
                    if (ShowMessageFX.YesNo("Are you sure you want to close this form?", psFormName, null)) {
                        if (poOnClose != null) {
                            poOnClose.run();
                        } else {
                            ((Stage) ChildAnchorPane.getScene().getWindow()).close();
                        }
                    }
                    break;
                default:
                    ShowMessageFX.Warning("Please contact admin to assist about no button available", psFormName, null);
                    break;
            }
        } catch (Exception ex) {
            Logger.getLogger(PurchaseOrderMP_LandingController.class.getName()).log(Level.SEVERE, MiscUtil.getException(ex), ex);
            ShowMessageFX.Error(MiscUtil.getException(ex), psFormName, null);
        }
    }

    private void initFilters() {
        //order type: All + the types of the entry screen
        ObservableList<String> loTypes = FXCollections.observableArrayList(PurchaseOrderStaticData.FILTER_ALL);
        loTypes.addAll(PurchaseOrderStaticData.ORDER_TYPE_DESCRIPTION);
        cmbPaymentFilter.setItems(loTypes);
        cmbPaymentFilter.getSelectionModel().selectFirst();
        JFXUtil.initComboBoxCellDesignColor("#FF8201", cmbPaymentFilter);
        cmbPaymentFilter.setOnAction(e -> reloadOnFilter());

        //date range: the last month up to today (set before the listeners, so nothing retrieves yet)
        setDefaultDateRange();

        if (cmbStatus != null) {
            cmbStatus.setItems(PurchaseOrderStaticData.STATUS_FILTER_DESCRIPTION);
            cmbStatus.getSelectionModel().selectFirst();
            JFXUtil.initComboBoxCellDesignColor("#FF8201", cmbStatus);
            //the status is part of the query, so it retrieves again
            cmbStatus.setOnAction(e -> reloadOnFilter());
        }

        //typing waits for a short pause, then retrieves again
        poSearchDelay.setOnFinished(e -> reloadOnFilter());
        tfSearch.textProperty().addListener((obs, oldValue, newValue) -> poSearchDelay.playFromStart());
        dpDateFrom.valueProperty().addListener((obs, oldValue, newValue) -> reloadOnFilter());
        dpDateTo.valueProperty().addListener((obs, oldValue, newValue) -> reloadOnFilter());
    }

    /** Date From = today minus the default range, Date To = today. */
    private void setDefaultDateRange() {
        LocalDate ldToday = LocalDate.now();
        dpDateTo.setValue(ldToday);
        dpDateFrom.setValue(ldToday.minusMonths(PurchaseOrderStaticData.FILTER_DEFAULT_DATE_RANGE_MONTHS));
    }

    /** A filter changed: retrieve again (not while the screen starts or the filters are being cleared). */
    private void reloadOnFilter() {
        if (pbResettingFilters || !pbStarted || po() == null) {
            return;
        }
        if (dpDateFrom.getValue() != null && dpDateTo.getValue() != null
                && dpDateFrom.getValue().isAfter(dpDateTo.getValue())) {
            return; //wait until the range is valid
        }
        loadTransactions();
    }

    private void clearFilters() {
        pbResettingFilters = true;
        try {
            tfSearch.clear();
            setDefaultDateRange();
            cmbPaymentFilter.getSelectionModel().selectFirst();
            if (cmbStatus != null) {
                cmbStatus.getSelectionModel().selectFirst();
            }
        } finally {
            pbResettingFilters = false;
        }
        loadTransactions();
    }

    private void initTable() {
        tblRowNo.setCellValueFactory(cd -> new SimpleStringProperty(
                String.valueOf(pnPage * pnPageSize + cd.getTableView().getItems().indexOf(cd.getValue()) + 1)));
        tblRowTransNo.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().transNo));
        tblRowDate.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().date));
        tblRowCustomer.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().supplier));
        tblRowTransNo1.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().reference));
        tblRowSource.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().orderType));
        tblRowTotal.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().total));
        tblRowStatus.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().statusText));

        tblTransactions.setPlaceholder(new Label("NO RECORD TO LOAD"));
        tblTransactions.setItems(FXCollections.observableArrayList());
        tblTransactions.getSelectionModel().selectedItemProperty().addListener((obs, oldRow, newRow) -> updateButtons());

        //double click = preview of the transaction (any status)
        tblTransactions.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && selected() != null) {
                openPreview();
            }
        });

        //same highlight as the entry screen's tables: Confirmed rows are blue
        tblTransactions.setRowFactory(tv -> new TableRow<Row>() {
            @Override
            protected void updateItem(Row item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setStyle("");
                } else if (PurchaseOrderStatus.CONFIRMED.equals(item.status)) {
                    setStyle("-fx-background-color: #A7C7E7;");
                } else if (PurchaseOrderStatus.VOID.equals(item.status)
                        || PurchaseOrderStatus.CANCELLED.equals(item.status)) {
                    setStyle("-fx-text-fill: #7A7A7A;");
                } else {
                    setStyle("");
                }
            }
        });
    }

    private void initPaging() {
        cmbPageSize.setItems(PurchaseOrderStaticData.FILTER_PAGE_SIZES);
        cmbPageSize.getSelectionModel().select(Integer.valueOf(pnPageSize));
        cmbPageSize.setOnAction(e -> {
            Integer lnSize = cmbPageSize.getValue();
            if (lnSize != null && lnSize != pnPageSize) {
                pnPageSize = lnSize;
                showPage(0);
            }
        });

        btnPageFirst.setOnAction(e -> showPage(0));
        btnPagePrev.setOnAction(e -> showPage(pnPage - 1));
        btnPageNext.setOnAction(e -> showPage(pnPage + 1));
        btnPageLast.setOnAction(e -> showPage(pageCount() - 1));
    }

    // =====================================================================
    // Retrieve, filter, page
    // =====================================================================
    /** Status letters sent to the query for the selected status filter (see PurchaseOrderStaticData). */
    private String statusCodes() {
        int lnIndex = cmbStatus == null ? 0 : cmbStatus.getSelectionModel().getSelectedIndex();
        if (lnIndex < 0 || lnIndex >= PurchaseOrderStaticData.STATUS_FILTER_CODE.length) {
            lnIndex = 0; //All
        }
        return PurchaseOrderStaticData.STATUS_FILTER_CODE[lnIndex];
    }

    /** Retrieves the purchase orders matching every filter (the filters are part of the query). */
    private void loadTransactions() {
        if (po() == null) {
            return;
        }
        btnRefresh.setDisable(true);
        paAllRows.clear();
        try {
            //a PO was opened: Master() no longer holds this screen's industry / company / category
            if (pbContextDirty) {
                if (!initPurchaseOrder()) {
                    ShowMessageFX.Warning("Unable to reset the purchase order list. "
                            + "Please check the application log.", psFormName, null);
                    return;
                }
                pbContextDirty = false;
            }
            po().setTransactionStatus(statusCodes());

            //order type: index 0 of the combo is "All", the rest follow ORDER_TYPE_CODE
            int lnType = cmbPaymentFilter.getSelectionModel().getSelectedIndex() - 1;
            String lsType = lnType >= 0 ? PurchaseOrderStaticData.ORDER_TYPE_CODE[lnType] : "";

            poJSON = po().getPurchaseOrder(tfSearch.getText(), dpDateFrom.getValue(), dpDateTo.getValue(), lsType);
            if (!"success".equals(poJSON.get("result"))) {
                ShowMessageFX.Warning(String.valueOf(poJSON.get("message")), psFormName, null);
            } else {
                for (int lnCtr = 0; lnCtr < po().getPOMasterCount(); lnCtr++) {
                    paAllRows.add(toRow(lnCtr));
                }
            }
        } catch (Exception ex) {
            Logger.getLogger(PurchaseOrderMP_LandingController.class.getName()).log(Level.SEVERE, MiscUtil.getException(ex), ex);
            ShowMessageFX.Error(MiscUtil.getException(ex), psFormName, null);
        } finally {
            btnRefresh.setDisable(false);
        }
        paFiltered.setAll(paAllRows);
        showPage(0);
        updateSummary();
    }

    /**
     * Summary cards over everything retrieved (all pages, every filter applied).
     * The groups follow PurchaseOrderStaticData.SUMMARY_*_STATUS, so the three
     * cards add up to the total.
     */
    private void updateSummary() {
        double[] lnAmount = new double[3]; //pending, approved, voided / returned
        int[] lnCount = new int[3];
        double lnTotal = 0;

        for (Row loRow : paAllRows) {
            lnTotal += loRow.amount;
            int lnGroup = summaryGroup(loRow.status);
            if (lnGroup >= 0) {
                lnAmount[lnGroup] += loRow.amount;
                lnCount[lnGroup]++;
            }
        }

        setSummary(lblTotalAmount, lblTotalCount, lnTotal, paAllRows.size());
        setSummary(lblPendingAmount, lblPendingCount, lnAmount[0], lnCount[0]);
        setSummary(lblApprovedAmount, lblApprovedCount, lnAmount[1], lnCount[1]);
        setSummary(lblVoidedAmount, lblVoidedCount, lnAmount[2], lnCount[2]);
    }

    /** 0 = pending, 1 = approved, 2 = voided / returned; -1 = in none of the cards. */
    private static int summaryGroup(String fsStatus) {
        if (Arrays.asList(PurchaseOrderStaticData.SUMMARY_PENDING_STATUS).contains(fsStatus)) {
            return 0;
        }
        if (Arrays.asList(PurchaseOrderStaticData.SUMMARY_APPROVED_STATUS).contains(fsStatus)) {
            return 1;
        }
        if (Arrays.asList(PurchaseOrderStaticData.SUMMARY_VOIDED_STATUS).contains(fsStatus)) {
            return 2;
        }
        return -1;
    }

    private static void setSummary(Label foAmount, Label foCount, double fnAmount, int fnCount) {
        foAmount.setText(MONEY.format(fnAmount));
        foCount.setText(fnCount + (fnCount == 1 ? " transaction" : " transactions"));
    }

    private Row toRow(int fnIndex) throws GuanzonException, java.sql.SQLException {
        Row loRow = new Row();
        loRow.transNo = text(po().POMaster(fnIndex).getTransactionNo());
        loRow.dateValue = CustomCommonUtil.parseDateStringToLocalDate(
                SQLUtil.dateFormat(po().POMaster(fnIndex).getTransactionDate(), SQLUtil.FORMAT_SHORT_DATE));
        loRow.date = loRow.dateValue == null ? "" : CustomCommonUtil.formatLocalDateToShortString(loRow.dateValue);
        loRow.supplier = text(po().POMaster(fnIndex).Supplier().getCompanyName());
        loRow.reference = text(po().POMaster(fnIndex).getReference());

        int lnType = Arrays.asList(PurchaseOrderStaticData.ORDER_TYPE_CODE)
                .indexOf(String.valueOf(po().POMaster(fnIndex).getValue("cOrdrType")));
        loRow.orderType = lnType >= 0 ? PurchaseOrderStaticData.ORDER_TYPE_DESCRIPTION.get(lnType) : "";

        loRow.amount = po().POMaster(fnIndex).getTranTotal().doubleValue();
        loRow.total = MONEY.format(loRow.amount);
        loRow.status = po().POMaster(fnIndex).getConvertedTransactionStatus();
        loRow.statusText = POStatus.screenLabel(po().POMaster(fnIndex).getTransactionStatus());
        loRow.printed = "1".equals(String.valueOf(po().POMaster(fnIndex).getPrint()));
        return loRow;
    }

    private int pageCount() {
        return Math.max(1, (int) Math.ceil((double) paFiltered.size() / pnPageSize));
    }

    private void showPage(int fnPage) {
        String lsSelected = selected() == null ? null : selected().transNo;

        pnPage = Math.max(0, Math.min(fnPage, pageCount() - 1));
        int lnFrom = pnPage * pnPageSize;
        int lnTo = Math.min(lnFrom + pnPageSize, paFiltered.size());
        tblTransactions.setItems(FXCollections.observableArrayList(
                paFiltered.isEmpty() ? new ArrayList<Row>() : paFiltered.subList(lnFrom, lnTo)));

        lblPage.setText("Page " + (pnPage + 1) + " of " + pageCount());
        lblRecordInfo.setText(paFiltered.isEmpty() ? "Showing 0 of 0"
                : "Showing " + (lnFrom + 1) + "-" + lnTo + " of " + paFiltered.size());

        btnPageFirst.setDisable(pnPage == 0);
        btnPagePrev.setDisable(pnPage == 0);
        btnPageNext.setDisable(pnPage >= pageCount() - 1);
        btnPageLast.setDisable(pnPage >= pageCount() - 1);

        //keep the same transaction selected after a reload
        if (lsSelected != null) {
            for (Row loRow : tblTransactions.getItems()) {
                if (loRow.transNo.equals(lsSelected)) {
                    tblTransactions.getSelectionModel().select(loRow);
                    break;
                }
            }
        }
        updateButtons();
    }

    // =====================================================================
    // Actions on the selected transaction
    // =====================================================================
    private Row selected() {
        return tblTransactions.getSelectionModel().getSelectedItem();
    }

    private static boolean isStatus(Row foRow, String... fsStatuses) {
        return foRow != null && Arrays.asList(fsStatuses).contains(foRow.status);
    }

    private static boolean canUpdate(Row foRow) {
        return isStatus(foRow, PurchaseOrderStatus.OPEN, PurchaseOrderStatus.RETURNED);
    }

    /** Buttons follow the status of the selected transaction. */
    private void updateButtons() {
        Row loRow = selected();
        btnUpdate.setDisable(!canUpdate(loRow));
        btnSave1.setDisable(!isStatus(loRow, PurchaseOrderStatus.OPEN, PurchaseOrderStatus.RETURNED));
        btnSave11.setDisable(!isStatus(loRow, PurchaseOrderStatus.CONFIRMED));
        btnReturn.setDisable(!isStatus(loRow, PurchaseOrderStatus.CONFIRMED));
        btnVoid.setDisable(!isStatus(loRow, PurchaseOrderStatus.OPEN, PurchaseOrderStatus.RETURNED));
        btnCancel.setDisable(!isStatus(loRow, PurchaseOrderStatus.CONFIRMED, PurchaseOrderStatus.APPROVED));
        btnPrint.setDisable(!isStatus(loRow, PurchaseOrderStatus.APPROVED, PurchaseOrderStatus.POSTED));
        btnExport.setDisable(!isStatus(loRow, PurchaseOrderStatus.APPROVED, PurchaseOrderStatus.POSTED));
        btnVoid1.setDisable(loRow == null);
        btnPrint.setText(loRow != null && loRow.printed ? "Reprint" : "Print");
    }

    /** Opens the selected transaction in the purchase order controller; false (with a message) if it fails. */
    private boolean openSelected() throws Exception {
        Row loRow = selected();
        if (loRow == null) {
            ShowMessageFX.Warning("Select a transaction first.", psFormName, null);
            return false;
        }
        pbContextDirty = true; //Master() is replaced by the opened PO, even if opening fails
        poJSON = po().OpenTransaction(loRow.transNo);
        if (!"success".equals(poJSON.get("result"))) {
            ShowMessageFX.Warning(String.valueOf(poJSON.get("message")), psFormName, null);
            return false;
        }
        return true;
    }

    /**
     * Opens PurchaseOrderMP_Preview.fxml for the selected transaction and waits until it is
     * closed. The transaction is opened in the PO controller first and handed to the preview
     * controller (the same way the entry screen hands its PO to the recipients screen).
     */
    private void openPreview() {
        openPreview(false);
    }

    /**
     * @param fbUpdate true (Update button): the preview opens in edit mode so the transaction can be modified
     */
    private void openPreview(boolean fbUpdate) {
        try {
            if (!openSelected()) {
                return;
            }

            URL loLocation = getClass().getResource(PurchaseOrderStaticData.PREVIEW_FXML);
            if (loLocation == null) {
                throw new IOException("Screen not found: " + PurchaseOrderStaticData.PREVIEW_FXML);
            }
            FXMLLoader loader = new FXMLLoader(loLocation);
            //the preview FXML has no fx:controller, so loader.getController() was null
            PurchaseOrderMP_PreviewController loPreview = new PurchaseOrderMP_PreviewController();
            loader.setController(loPreview);
            Parent root = loader.load();

            loPreview.setGRider(poApp);
            loPreview.setPurchaseOrder(po());
            loPreview.loadForm(fbUpdate);   // must be AFTER the setters

            Stage loStage = new Stage();
            loStage.setTitle(fbUpdate ? "Update Purchase Order" : "Purchase Order Preview");
            loStage.initStyle(StageStyle.UNDECORATED);
            loStage.initModality(Modality.APPLICATION_MODAL);
            loStage.initOwner(tblTransactions.getScene().getWindow());

            //movable by dragging, as the entry screen's modals
            final double[] lnOffset = new double[2];
            root.setOnMousePressed(e -> {
                lnOffset[0] = e.getSceneX();
                lnOffset[1] = e.getSceneY();
            });
            root.setOnMouseDragged(e -> {
                loStage.setX(e.getScreenX() - lnOffset[0]);
                loStage.setY(e.getScreenY() - lnOffset[1]);
            });

            loStage.setScene(new Scene(root));
            loStage.setResizable(false);
            loStage.showAndWait();

            loadTransactions(); //the preview may have changed the status
        } catch (Exception ex) {
            Logger.getLogger(PurchaseOrderMP_LandingController.class.getName())
                    .log(Level.SEVERE, "Failed to load the Purchase Order preview", ex);
            ShowMessageFX.Error("Unable to open the purchase order preview.", psFormName, null);
        }
    }

    private void updateSelected() {
        Row loRow = selected();
        if (!canUpdate(loRow)) {
            ShowMessageFX.Warning("Only an open or returned purchase order can be modified.", psFormName, null);
            return;
        }
        openPreview(true); //the preview opens in edit mode
    }

    private interface StatusCall {
        JSONObject run(String fsRemarks) throws Exception;
    }

    /** Asks, opens the selected transaction, runs the status change, then reloads the list. */
    private void runStatusAction(String fsTitle, String fsQuestion, boolean fbRemarks, StatusCall foCall) throws Exception {
        if (selected() == null) {
            ShowMessageFX.Warning("Select a transaction first.", psFormName, null);
            return;
        }
        if (!ShowMessageFX.YesNo(null, psFormName, fsQuestion)) {
            return;
        }

        String lsRemarks = "";
        if (fbRemarks) {
            Optional<String> loRemarks = askRemarks(fsTitle);
            if (!loRemarks.isPresent() || loRemarks.get().trim().isEmpty()) {
                ShowMessageFX.Warning("Remarks are required to " + fsTitle.toLowerCase() + " a purchase order.", psFormName, null);
                return;
            }
            lsRemarks = loRemarks.get().trim();
        }

        if (!openSelected()) {
            return;
        }
        po().setWithUI(true);
        poJSON = foCall.run(lsRemarks);
        if ("success".equals(poJSON.get("result"))) {
            ShowMessageFX.Information(String.valueOf(poJSON.get("message")), psFormName, null);
        } else {
            ShowMessageFX.Warning(String.valueOf(poJSON.get("message")), psFormName, null);
        }
        loadTransactions();
    }

    private Optional<String> askRemarks(String fsTitle) {
        TextInputDialog loDialog = new TextInputDialog();
        loDialog.setTitle(psFormName);
        loDialog.setHeaderText(fsTitle + " Purchase Order");
        loDialog.setContentText("Remarks:");
        return loDialog.showAndWait();
    }

    private void printSelected() throws Exception {
        if (!openSelected()) {
            return;
        }
        poJSON = po().printTransaction(PurchaseOrderStaticData.Printing_CARSp_MCSp_General);
        if ("error".equals(poJSON.get("result"))) {
            ShowMessageFX.Warning(String.valueOf(poJSON.get("message")), "Print Purchase Order", null);
            return;
        }
        loadTransactions(); //the Print / Reprint flag may have changed
    }

    private void exportSelected() throws Exception {
        if (!openSelected()) {
            return;
        }
        poJSON = po().PdfExporter().export(PurchaseOrderStaticData.Printing_CARSp_MCSp_General);
        if ("error".equals(poJSON.get("result"))) {
            ShowMessageFX.Warning(String.valueOf(poJSON.get("message")), "Export Purchase Order", null);
        } else if (poJSON.get("message") != null) {
            ShowMessageFX.Information(String.valueOf(poJSON.get("message")), "Export Purchase Order", null);
        }
    }

    private void showHistory() throws Exception {
        if (!openSelected()) {
            return;
        }
        try {
            po().ShowStatusHistory();
        } catch (NullPointerException npe) {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, MiscUtil.getException(npe), npe);
            ShowMessageFX.Error("No transaction status history to load!", psFormName, null);
        }
    }

    // =====================================================================
    // Helpers
    // =====================================================================
    private static String text(Object foValue) {
        return foValue == null ? "" : foValue.toString();
    }

    /** One line of the transaction list. */
    private static final class Row {

        String transNo = "";
        String date = "";
        LocalDate dateValue;
        String supplier = "";
        String reference = "";
        String orderType = "";
        String total = "";
        double amount;
        /** Status letter (PurchaseOrderStatus) and its screen label. */
        String status = "";
        String statusText = "";
        boolean printed;
    }
}