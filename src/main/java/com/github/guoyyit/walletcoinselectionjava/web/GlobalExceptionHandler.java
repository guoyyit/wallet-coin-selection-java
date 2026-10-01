package com.github.guoyyit.walletcoinselectionjava.web;

import com.github.guoyyit.walletcoinselectionjava.exception.ConcurrentConflictException;
import com.github.guoyyit.walletcoinselectionjava.exception.FeeLimitExceededException;
import com.github.guoyyit.walletcoinselectionjava.exception.InsufficientBalanceException;
import com.github.guoyyit.walletcoinselectionjava.exception.NoAvailableUtxoException;
import com.github.guoyyit.walletcoinselectionjava.exception.WalletException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * 将各类钱包异常映射为可区分的 HTTP 错误响应：
 * <ul>
 *     <li>余额不足 / 费率超限 / 无可用输入：422 UNPROCESSABLE_ENTITY</li>
 *     <li>并发冲突：409 CONFLICT（响应体含冲突 UTXO id）</li>
 *     <li>非法参数：400 BAD_REQUEST</li>
 *     <li>构建不存在：404 NOT_FOUND</li>
 *     <li>状态错误（已提交/已取消/已过期再操作）：409 CONFLICT</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    public record ErrorResponse(String error, String message, List<String> details) {
    }

    @ExceptionHandler(ConcurrentConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConcurrentConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(simpleName(e), e.getMessage(), e.conflictingUtxoIds()));
    }

    @ExceptionHandler({
            InsufficientBalanceException.class,
            FeeLimitExceededException.class,
            NoAvailableUtxoException.class
    })
    public ResponseEntity<ErrorResponse> handleUnprocessable(WalletException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse(simpleName(e), e.getMessage(), List.of()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("IllegalArgument", e.getMessage(), List.of()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NotFound", e.getMessage(), List.of()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleState(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("IllegalState", e.getMessage(), List.of()));
    }

    private static String simpleName(Exception e) {
        return e.getClass().getSimpleName();
    }
}
