package ph.com.guanzongroup.cas.po.controller;

import static ph.com.guanzongroup.cas.po.controller.TestKit.ok;
import static ph.com.guanzongroup.cas.po.controller.TestKit.smart;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.rowset.CachedRowSet;
import org.json.simple.JSONObject;
import ph.com.guanzongroup.cas.po.validator.PurchaseOrder_General;

/**
 * What the test controllers return instead of the appdriver framework and the
 * helper classes (database work), and what they record.
 */
final class FrameworkStub {

    JSONObject initResult = ok();
    JSONObject newResult = ok();
    JSONObject openResult = ok();
    JSONObject updateResult = ok();
    JSONObject saveResult = ok();
    JSONObject addDetailResult = ok();
    JSONObject statusResult = ok();
    SQLException statusError;

    final List<String> statuses = new ArrayList<>();
    final List<String> statusRemarks = new ArrayList<>();
    String openedNo;
    int saves;

    CachedRowSet approvalHistory;
    CachedRowSet statusHistory;
    final List<Object[]> shownHistory = new ArrayList<>();

    /** true: the controllers build their real helpers (create... methods). */
    boolean realHelpers;

    final PurchaseOrder_General validator = smart(PurchaseOrder_General.class);
    final PurchaseOrderSearch search = smart(PurchaseOrderSearch.class);
    final PurchaseOrderAttachments attachments = smart(PurchaseOrderAttachments.class);
    final PurchaseOrderAudit audit = smart(PurchaseOrderAudit.class);
    final PurchaseOrderSuggestions suggestions = smart(PurchaseOrderSuggestions.class);
    final PurchaseOrderSourceDocuments sourceDocuments = smart(PurchaseOrderSourceDocuments.class);
    final PurchaseOrderAdvancePayment advancePayment = smart(PurchaseOrderAdvancePayment.class);
    final PurchaseOrderPrinter printer = smart(PurchaseOrderPrinter.class);

    JSONObject status(String fsStatus, String fsRemarks) throws SQLException {
        if (statusError != null) {
            throw statusError;
        }
        statuses.add(fsStatus);
        statusRemarks.add(fsRemarks);
        return statusResult;
    }
}
