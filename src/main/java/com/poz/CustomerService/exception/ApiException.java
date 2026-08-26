package com.poz.CustomerService.exception;

import org.springframework.http.HttpStatus;

/**
 * Service 層統一往外丟的例外，之後由 Controller 的 @RestControllerAdvice
 * 接住並轉成 docs/api.md 定義的 ErrorResponse：
 *
 * <pre>
 * { "code": "INVALID_STATUS_TRANSITION", "message": "無法從「已解決」變更為「待客戶回覆」" }
 * </pre>
 *
 * <h2>為什麼把 HTTP 狀態碼放在例外身上</h2>
 * 「找不到工單」該回 404、「非法狀態轉換」該回 400，這件事只有丟例外的那個人最清楚。
 * 讓 Service 決定，Controller 那邊就不必寫一大串 if-else 去猜該回什麼碼。
 *
 * <h2>為什麼繼承 RuntimeException 而不是 Exception</h2>
 * Exception 是「受檢例外」，每一層呼叫都得寫 throws 或 try-catch，
 * 但這些錯誤本來就不是呼叫端能當場處理的（找不到就是找不到），
 * 一路往上丟到統一的地方處理才合理。
 * 附帶一提，Spring 的交易預設只在遇到 RuntimeException 時才回滾，
 * 用受檢例外的話還要多設定 rollbackFor。
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    // ---- 底下是常用的幾種，讓呼叫端讀起來像句子：ApiException.notFound(...) ----

    /** 400：參數格式錯誤、非法的狀態轉換。 */
    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    /** 401：未登入或帳密錯誤。 */
    public static ApiException unauthorized(String code, String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, code, message);
    }

    /** 403：已登入但無權限操作該筆資料。 */
    public static ApiException forbidden(String code, String message) {
        return new ApiException(HttpStatus.FORBIDDEN, code, message);
    }

    /** 404：工單／客服不存在。 */
    public static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }
}
