package com.poz.CustomerService.exception;

import org.springframework.http.HttpStatus;

/**
 * Service 層統一往外丟的例外，之後由 Controller 的 @RestControllerAdvice
 * 接住並轉成統一的 ErrorResponse：
 *
 * <pre>
 * { "code": "INVALID_STATUS_TRANSITION", "message": "無法從「已解決」變更為「待客戶回覆」" }
 * </pre>
 *
 * <p>
 * 狀態碼放在例外身上，是因為「該回 404 還是 400」只有丟例外的那個人最清楚，
 * Controller 就不必寫一串 if-else 去猜。
 * <p>
 * 繼承 RuntimeException 而不是 Exception：受檢例外每一層都得寫 throws 或 try-catch，
 * 但這些錯誤本來就不是呼叫端能當場處理的。而且 Spring 的交易預設只對 RuntimeException 回滾。
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    /**
     * 一般不直接用這支，用下面四個靜態方法可讀性比較好。
     *
     * @param status  {@code HttpStatus}——要回給前端的狀態碼，例如 {@code HttpStatus.NOT_FOUND}
     * @param code    {@code String}——錯誤代號，固定大寫英文，例如 {@code AGENT_NOT_FOUND}
     * @param message {@code String}——給人看的中文訊息，會原封不動出現在回應裡，
     *                所以<b>不要寫進資料表名稱、SQL 片段</b>
     */
    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    /**
     * 這個錯誤該回的 HTTP 狀態碼。
     *
     * @return {@code HttpStatus}——例如 {@code HttpStatus.NOT_FOUND}，
     *         由 GlobalExceptionHandler 照搬使用
     */
    public HttpStatus getStatus() {
        return status;
    }

    /**
     * 給程式判斷用的錯誤代號。
     *
     * @return {@code String}——例如 {@code INVALID_CREDENTIALS}，原封不動放進回應的 code 欄位
     */
    public String getCode() {
        return code;
    }

    // ---- 底下是常用的幾種，讓呼叫端讀起來像句子：ApiException.notFound(...) ----

    /**
     * 400：參數格式錯誤、非法的狀態轉換。
     *
     * @param code    {@code String}——錯誤代號，例如 {@code INVALID_STATUS_TRANSITION}
     * @param message {@code String}——給人看的中文訊息
     * @return {@link ApiException}——<b>還沒被丟出</b>，要自己 {@code throw} 或交給 {@code orElseThrow}
     */
    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    /**
     * 401：未登入或帳密錯誤。
     *
     * @param code    {@code String}——錯誤代號，例如 {@code INVALID_CREDENTIALS}
     * @param message {@code String}——給人看的中文訊息
     * @return {@link ApiException}——還沒被丟出
     */
    public static ApiException unauthorized(String code, String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, code, message);
    }

    /**
     * 403：已登入但無權限操作該筆資料。
     *
     * @param code    {@code String}——錯誤代號
     * @param message {@code String}——給人看的中文訊息
     * @return {@link ApiException}——還沒被丟出
     */
    public static ApiException forbidden(String code, String message) {
        return new ApiException(HttpStatus.FORBIDDEN, code, message);
    }

    /**
     * 404：工單／客服不存在。
     *
     * @param code    {@code String}——錯誤代號，例如 {@code AGENT_NOT_FOUND}
     * @param message {@code String}——給人看的中文訊息
     * @return {@link ApiException}——還沒被丟出
     */
    public static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    /**
     * 409：資料已經存在，再做一次會撞到唯一約束。
     * <p>
     * 跟 400 的差別在於「錯的是什麼」：400 是這次送來的內容本身不合格（格式、長度、非法轉換），
     * 409 是內容沒問題、但跟資料庫現有的狀態衝突了。前端的處理方式也不一樣——
     * 400 要使用者改輸入，409 通常是「你已經做過了」。
     *
     * @param code    {@code String}——錯誤代號，例如 {@code FOLLOW_UP_ALREADY_EXISTS}
     * @param message {@code String}——給人看的中文訊息
     * @return {@link ApiException}——還沒被丟出
     */
    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
