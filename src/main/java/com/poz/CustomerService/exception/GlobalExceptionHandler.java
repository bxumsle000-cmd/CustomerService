package com.poz.CustomerService.exception;

import com.poz.CustomerService.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全專案共用的例外處理，把所有 Controller 丟出的例外統一轉成 {@link ErrorResponse}。
 * <p>
 * 分工的原則是「這是誰的錯」：<b>用戶端送錯</b>的一律回 4xx，並且把哪裡錯了講清楚，
 * 因為前端看得懂才改得掉；<b>伺服器自己爆掉</b>才回 500，而且只回一句罐頭訊息，
 * 真正的原因寫進 log。
 * <p>
 * 最底下那支 {@code handleUnexpected} 接的是 {@link Exception}，會把所有沒被上面接走的
 * 例外都吃掉。所以每漏接一種「用戶端送錯」的例外，它就會變成一個假的 500——
 * 前端看到 500 會以為是後端壞了，實際上是自己參數打錯。上面那幾支 4xx 的存在意義就在這裡。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 處理自訂的 {@link ApiException}，狀態碼與 code 照搬。
     *
     * @param e Service 層丟出來的例外，由 Spring 自動傳進來
     * @return 狀態碼取自 {@code e.getStatus()}，body 是 {@code { code, message }}
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException e) {
        return ResponseEntity
                .status(e.getStatus())
                .body(new ErrorResponse(e.getCode(), e.getMessage()));
    }

    /**
     * 處理 DTO 驗證失敗（{@code @NotBlank} / {@code @Size} / {@code @Pattern} 沒過）。
     *
     * @param e Spring 丟的例外，帶著所有不合格欄位
     * @return 400，code 固定 {@code VALIDATION_ERROR}，message 取第一個不合格欄位；
     *         取不到時退回「參數格式錯誤」
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("參數格式錯誤");

        return ResponseEntity
                .badRequest()
                .body(new ErrorResponse("VALIDATION_ERROR", message));
    }

    /**
     * 少帶了必填的查詢參數，例如 {@code GET /api/calendar} 沒帶 {@code year}。
     *
     * @param e Spring 丟的例外，帶著缺少的參數名稱
     * @return 400 / {@code VALIDATION_ERROR}，訊息會指名是哪個參數
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(
            MissingServletRequestParameterException e) {
        return ResponseEntity
                .badRequest()
                .body(new ErrorResponse("VALIDATION_ERROR",
                        "缺少必要參數：" + e.getParameterName()));
    }

    /**
     * 參數型別對不上，例如 {@code year=abc} 塞不進 {@code int}、
     * {@code createdFrom=昨天} 轉不成時間。
     *
     * @param e Spring 丟的例外，帶著轉換失敗的參數名稱
     * @return 400 / {@code VALIDATION_ERROR}，訊息會指名是哪個參數
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException e) {
        return ResponseEntity
                .badRequest()
                .body(new ErrorResponse("VALIDATION_ERROR",
                        "參數格式錯誤：" + e.getName()));
    }

    /**
     * request body 讀不出來：JSON 語法壞掉、編碼不是 UTF-8、或整個 body 沒帶。
     * <p>
     * 訊息刻意寫死一句罐頭，<b>不</b>把 {@code e.getMessage()} 回給前端——
     * 那裡面會夾帶 Jackson 的內部細節，甚至原封不動印出送進來的片段。
     * 詳細原因寫進 log 就好。
     *
     * @param e Spring 丟的例外，完整原因會寫進 log
     * @return 400 / {@code MALFORMED_JSON}
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e) {
        log.warn("request body 讀取失敗：{}", e.getMessage());

        return ResponseEntity
                .badRequest()
                .body(new ErrorResponse("MALFORMED_JSON", "請求內容格式錯誤，請確認是合法的 JSON"));
    }

    /**
     * 路徑對但 HTTP method 用錯，例如對 {@code /api/calendar} 送 PUT。
     *
     * @param e Spring 丟的例外，帶著這個路徑允許哪些 method
     * @return 405 / {@code METHOD_NOT_ALLOWED}
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException e) {
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ErrorResponse("METHOD_NOT_ALLOWED",
                        "這個路徑不支援 " + e.getMethod() + " 方法"));
    }

    /**
     * 路徑根本不存在，例如把 {@code /api/calendar} 打成 {@code /api/tickets/calendar}。
     * <p>
     * 沒有這一支的話，打錯路徑會掉進最底下的 {@code handleUnexpected} 變成 500，
     * 前端會誤以為後端掛了。
     *
     * @param e Spring 丟的例外
     * @return 404 / {@code NOT_FOUND}
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NOT_FOUND", "找不到這個路徑：" + e.getResourcePath()));
    }

    /**
     * 處理未預期的例外，例如資料庫斷線、NullPointerException。
     * 真正的原因只寫進 log，不回給前端。
     *
     * @param e 任何沒被上面幾支接走的例外，完整堆疊會寫進 log
     * @return 500，固定 {@code INTERNAL_ERROR} / 「系統發生錯誤，請稍後再試」
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("未預期的錯誤", e);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "系統發生錯誤，請稍後再試"));
    }
}
