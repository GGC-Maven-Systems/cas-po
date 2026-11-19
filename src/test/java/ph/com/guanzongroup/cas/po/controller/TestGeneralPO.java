package ph.com.guanzongroup.cas.po.controller;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.sql.rowset.CachedRowSet;
import org.guanzon.appdriver.agent.services.Model;
import org.guanzon.appdriver.base.GRiderCAS;
import org.json.simple.JSONObject;
import ph.com.guanzongroup.cas.po.model.Model_PO_Detail;
import ph.com.guanzongroup.cas.po.model.Model_PO_Master;
import ph.com.guanzongroup.cas.po.validator.PurchaseOrderValidator;

/** PurchaseOrderGeneral without the database: framework and helpers come from {@link FrameworkStub}. */
class TestGeneralPO extends PurchaseOrderGeneral {

    final FrameworkStub stub = new FrameworkStub();

    // ---- wiring (protected framework fields) ----
    void wire(GRiderCAS foGRider, Model_PO_Master foMaster) {
        poGRider = foGRider;
        poMaster = foMaster;
        paDetail = new ArrayList<>();
        SOURCE_CODE = "PO";
    }

    void mode(int fnMode) {
        pnEditMode = fnMode;
    }

    void parent(boolean fbParent) {
        pbWthParent = fbParent;
    }

    void tranStat(String fsStatus) {
        psTranStat = fsStatus;
    }

    void branch(String fsBranch) {
        psBranchCode = fsBranch;
    }

    void rows(Model... faRows) {
        paDetail = new ArrayList<>(Arrays.asList(faRows));
    }

    List<Model> removed() {
        return paDetailRemoved;
    }

    String sqlBrowse() {
        return SQL_BROWSE;
    }

    JSONObject lastResult() {
        return poJSON;
    }

    // ---- framework ----
    @Override
    protected JSONObject initialize() {
        return stub.initResult;
    }

    @Override
    protected JSONObject newTransaction() {
        return stub.newResult;
    }

    @Override
    protected JSONObject openTransaction(String fsTransNo) {
        stub.openedNo = fsTransNo;
        return stub.openResult;
    }

    @Override
    protected JSONObject updateTransaction() {
        return stub.updateResult;
    }

    @Override
    protected JSONObject saveTransaction() {
        stub.saves++;
        return stub.saveResult;
    }

    @Override
    protected JSONObject addDetail() {
        if ("success".equals(stub.addDetailResult.get("result"))) {
            paDetail.add(TestKit.smart(Model_PO_Detail.class));
        }
        return stub.addDetailResult;
    }

    @Override
    protected JSONObject statusChange(String fsTable, String fsTransNo, String fsRemarks, String fsStatus,
            boolean fbConfirm, boolean fbParent) throws SQLException {
        return stub.status(fsStatus, fsRemarks);
    }

    @Override
    protected CachedRowSet getApprovalHistory() {
        return stub.approvalHistory;
    }

    @Override
    protected CachedRowSet getStatusHistory() {
        return stub.statusHistory;
    }

    @Override
    protected void showStatusHistoryUI(String fsTitle, String fsTransNo, String fsEntryBy, String fsEntryDate, CachedRowSet foRows) {
        stub.shownHistory.add(new Object[]{"status", fsTransNo, fsEntryBy, fsEntryDate, foRows});
    }

    @Override
    protected void showApprovalHistoryUI(String fsTitle, String fsTransNo, String fsEntryBy, String fsEntryDate,
            double fnTotal, CachedRowSet foRows) {
        stub.shownHistory.add(new Object[]{"approval", fsTransNo, fsEntryBy, fsEntryDate, fnTotal, foRows});
    }

    // ---- helpers (mocks unless realHelpers) ----
    @Override
    protected PurchaseOrderSearch createSearch() {
        return stub.realHelpers ? super.createSearch() : stub.search;
    }

    @Override
    protected PurchaseOrderAttachments createAttachments() {
        return stub.realHelpers ? super.createAttachments() : stub.attachments;
    }

    @Override
    protected PurchaseOrderAudit createAudit() {
        return stub.realHelpers ? super.createAudit() : stub.audit;
    }

    @Override
    protected PurchaseOrderSuggestions createSuggestions() {
        return stub.realHelpers ? super.createSuggestions() : stub.suggestions;
    }

    @Override
    protected PurchaseOrderValidator createValidator() {
        return stub.realHelpers ? super.createValidator() : stub.validator;
    }

    @Override
    protected PurchaseOrderSourceDocuments createSourceDocuments() {
        return stub.realHelpers ? super.createSourceDocuments() : stub.sourceDocuments;
    }

    @Override
    protected PurchaseOrderPrinter createPrinter() {
        return stub.realHelpers ? super.createPrinter() : stub.printer;
    }

    @Override
    protected PurchaseOrderAdvancePayment createAdvancePayment() {
        return stub.realHelpers ? super.createAdvancePayment() : stub.advancePayment;
    }
}
