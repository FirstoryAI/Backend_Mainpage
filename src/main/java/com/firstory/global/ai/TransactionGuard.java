package com.firstory.global.ai;

import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * AI 응답은 35초까지 걸릴 수 있어서 DB 트랜잭션 안에서 부르면 커넥션을 오래 붙잡는다.
 * AiClient 구현은 호출마다 이것을 먼저 부른다. 트랜잭션 안이면 바로 실패해서 테스트에서 드러난다.
 */
final class TransactionGuard {

    private TransactionGuard() {
    }

    static void requireNoTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("DB 트랜잭션 안에서 AI를 부르지 않아요. 조회 → AI 호출 → 저장 순서로 트랜잭션을 나눠요.");
        }
    }
}
