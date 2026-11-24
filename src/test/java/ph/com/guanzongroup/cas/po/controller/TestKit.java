package ph.com.guanzongroup.cas.po.controller;

import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.simple.JSONObject;
import org.mockito.stubbing.Answer;

/** Helpers for the controller unit tests (no database, no screen). */
final class TestKit {

    private TestKit() {
    }

    /** Mock whose JSONObject methods return success unless stubbed. */
    static final Answer<Object> JSON_SUCCESS = inv -> {
        if (inv.getMethod().getReturnType() == JSONObject.class) {
            return ok();
        }
        return RETURNS_DEFAULTS.answer(inv);
    };

    static <T> T smart(Class<T> foClass) {
        return mock(foClass, JSON_SUCCESS);
    }

    @SuppressWarnings("unchecked")
    static JSONObject ok() {
        JSONObject loJSON = new JSONObject();
        loJSON.put("result", "success");
        return loJSON;
    }

    @SuppressWarnings("unchecked")
    static JSONObject ok(String fsMessage) {
        JSONObject loJSON = ok();
        loJSON.put("message", fsMessage);
        return loJSON;
    }

    @SuppressWarnings("unchecked")
    static JSONObject err(String fsMessage) {
        JSONObject loJSON = new JSONObject();
        loJSON.put("result", "error");
        loJSON.put("message", fsMessage);
        return loJSON;
    }

    /** JSON from key / value pairs. */
    @SuppressWarnings("unchecked")
    static JSONObject json(Object... faPairs) {
        JSONObject loJSON = new JSONObject();
        for (int lnCtr = 0; lnCtr < faPairs.length; lnCtr += 2) {
            loJSON.put(faPairs[lnCtr], faPairs[lnCtr + 1]);
        }
        return loJSON;
    }

    static Map<String, Object> row(Object... faPairs) {
        Map<String, Object> loRow = new LinkedHashMap<>();
        for (int lnCtr = 0; lnCtr < faPairs.length; lnCtr += 2) {
            loRow.put((String) faPairs[lnCtr], faPairs[lnCtr + 1]);
        }
        return loRow;
    }

    /** In-memory ResultSet: next, getString / getDouble / getDate / getObject by label, close. */
    @SafeVarargs
    static ResultSet rows(Map<String, Object>... faRows) {
        final List<Map<String, Object>> laRows = new ArrayList<>(Arrays.asList(faRows));
        final int[] lnRow = {-1};
        return (ResultSet) Proxy.newProxyInstance(TestKit.class.getClassLoader(), new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "next":
                            lnRow[0]++;
                            return lnRow[0] < laRows.size();
                        case "close":
                            return null;
                        case "isClosed":
                        case "wasNull":
                            return false;
                        case "getString": {
                            Object loValue = laRows.get(lnRow[0]).get((String) args[0]);
                            return loValue == null ? null : loValue.toString();
                        }
                        case "getDouble": {
                            Object loValue = laRows.get(lnRow[0]).get((String) args[0]);
                            return loValue == null ? 0.0 : ((Number) loValue).doubleValue();
                        }
                        case "getDate":
                        case "getObject":
                            return laRows.get(lnRow[0]).get((String) args[0]);
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "equals":
                            return proxy == args[0];
                        case "toString":
                            return "rows" + laRows;
                        default:
                            throw new UnsupportedOperationException("rows." + method.getName());
                    }
                });
    }
}
