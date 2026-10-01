package com.github.guoyyit.walletcoinselectionjava.api;

import com.github.guoyyit.walletcoinselectionjava.error.FeeRateExceededException;
import com.github.guoyyit.walletcoinselectionjava.error.InsufficientFundsException;
import com.github.guoyyit.walletcoinselectionjava.error.NoSpendableUtxoException;
import com.github.guoyyit.walletcoinselectionjava.error.SessionNotFoundException;
import com.github.guoyyit.walletcoinselectionjava.error.UtxoLockConflictException;
import com.github.guoyyit.walletcoinselectionjava.error.WalletException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 将钱包异常映射为可区分的 HTTP 错误响应。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({
            InsufficientFundsException.class,
            NoSpendableUtxoException.class,
            FeeRateExceededException.class})
    public ResponseEntity<ErrorResponse> badRequest(WalletException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(exception.errorCode(), exception.getMessage()));
    }

    @ExceptionHandler(UtxoLockConflictException.class)
    public ResponseEntity<ErrorResponse> conflict(UtxoLockConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(exception.errorCode(), exception.getMessage()));
    }

    @ExceptionHandler(SessionNotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(SessionNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(exception.errorCode(), exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> illegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("BAD_REQUEST", exception.getMessage()));
    }

    public record ErrorResponse(String error, String message) {
    }
}
