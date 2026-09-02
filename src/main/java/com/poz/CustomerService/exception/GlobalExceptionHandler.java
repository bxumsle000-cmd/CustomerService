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
 * 全專案共用的例外處理，把所有 Controller 丟出的例外統一轉成 {@link ErrorResponse}。
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
     * 處理未預期的例外，例如資料庫斷線、NullPointerException。
     * 真正的原因只寫進 log，不回給前端。
     *
     * @param e 任何沒被上面兩支接走的例外，完整堆疊會寫進 log
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
