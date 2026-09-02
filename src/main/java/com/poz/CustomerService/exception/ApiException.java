package com.poz.CustomerService.exception;

import org.springframework.http.HttpStatus;

/**
 * Service 層統一往外丟的例外，由 {@link GlobalExceptionHandler} 接住並轉成
 * {@link com.poz.CustomerService.dto.ErrorResponse}。狀態碼與錯誤代號都由丟的那一方決定。
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    /**
     * 建立例外。一般改用下面的靜態方法可讀性比較好。
     *
     * @param status  要回給前端的狀態碼，例如 {@code HttpStatus.NOT_FOUND}
     * @param code    錯誤代號，固定大寫英文，例如 {@code AGENT_NOT_FOUND}
     * @param message 給人看的中文訊息，會原封不動出現在回應裡，
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
     * @return 例如 {@code HttpStatus.NOT_FOUND}
     */
    public HttpStatus getStatus() {
        return status;
    }

    /**
     * 給程式判斷用的錯誤代號。
     *
     * @return 例如 {@code INVALID_CREDENTIALS}，原封不動放進回應的 code 欄位
     */
    public String getCode() {
        return code;
    }

    /**
     * 400：參數格式錯誤、非法的狀態轉換。
     *
     * @param code    錯誤代號，例如 {@code INVALID_STATUS_TRANSITION}
     * @param message 給人看的中文訊息
     * @return 例外物件，<b>還沒被丟出</b>
     */
    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    /**
     * 401：未登入或帳密錯誤。
     *
     * @param code    錯誤代號，例如 {@code INVALID_CREDENTIALS}
     * @param message 給人看的中文訊息
     * @return 例外物件，還沒被丟出
     */
    public static ApiException unauthorized(String code, String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, code, message);
    }

    /**
     * 403：已登入但無權限操作該筆資料。
     *
     * @param code    錯誤代號
     * @param message 給人看的中文訊息
     * @return 例外物件，還沒被丟出
     */
    public static ApiException forbidden(String code, String message) {
        return new ApiException(HttpStatus.FORBIDDEN, code, message);
    }

    /**
     * 404：工單／客服不存在。
     *
     * @param code    錯誤代號，例如 {@code AGENT_NOT_FOUND}
     * @param message 給人看的中文訊息
     * @return 例外物件，還沒被丟出
     */
    public static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    /**
     * 409：資料已經存在，再做一次會撞到唯一約束。
     *
     * @param code    錯誤代號，例如 {@code FOLLOW_UP_ALREADY_EXISTS}
     * @param message 給人看的中文訊息
     * @return 例外物件，還沒被丟出
     */
    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
