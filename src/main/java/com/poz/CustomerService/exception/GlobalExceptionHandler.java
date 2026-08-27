package com.poz.CustomerService.exception;

import com.poz.CustomerService.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全專案共用的例外處理，把各種例外統一轉成 docs/api.md 定義的 {@link ErrorResponse}。
 * <p>
 * {@code @RestControllerAdvice} 會讓<b>所有</b> Controller 丟出的例外先經過這裡，
 * 所以每支 Controller 都不必寫 try-catch。沒有這一層的話，
 * Service 丟的 ApiException 會變成 Spring 預設的 500，精心設計的 401 / 404 全白費。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 我們自己丟的例外。狀態碼與 code 都由丟的那一方決定，這裡照搬就好。
     *
     * @param e {@link ApiException}——Service 層丟出來的，由 Spring 自動傳進來
     * @return {@code ResponseEntity<ErrorResponse>}——狀態碼取自 {@code e.getStatus()}，
     *         body 是 {@code { code, message }}
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException e) {
        return ResponseEntity
                .status(e.getStatus())
                .body(new ErrorResponse(e.getCode(), e.getMessage()));
    }

    /**
     * DTO 上的 {@code @NotBlank} / {@code @Size} / {@code @Pattern} 沒過時，
     * Spring 丟的就是這個（前提是 Controller 的參數有加 {@code @Valid}）。
     * <p>
     * 一次可能有很多個欄位不合格，但畫面通常只顯示一句，所以取第一個就好；
     * 訊息就是 DTO 註解裡寫的那句 message。
     *
     * @param e {@code MethodArgumentNotValidException}——Spring 丟的，帶著所有不合格欄位
     * @return {@code ResponseEntity<ErrorResponse>}——400，code 固定 {@code VALIDATION_ERROR}，
     *         message 取第一個不合格欄位；取不到時退回「參數格式錯誤」
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
     * 沒被上面接走的都算「我們沒預料到的錯」，例如資料庫斷線、NullPointerException。
     *
     * <b>訊息刻意寫得很籠統。</b>真正的原因寫進 log 給我們自己看，
     * 不要回給前端——例外訊息常常夾帶資料表名稱、SQL 片段這類內部細節，
     * 送出去等於免費提供情報給想攻擊的人。
     *
     * @param e {@code Exception}——任何沒被上面兩支接走的，完整堆疊會寫進 log
     * @return {@code ResponseEntity<ErrorResponse>}——500，固定
     *         {@code INTERNAL_ERROR} / 「系統發生錯誤，請稍後再試」，內容不隨 e 改變
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("未預期的錯誤", e);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "系統發生錯誤，請稍後再試"));
    }
}
