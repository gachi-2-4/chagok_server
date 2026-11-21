package gachi.chagok_server.global.common;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 트랜잭션이 커밋된 뒤에 할 일을 등록한다. 트랜잭션이 없으면 바로 실행한다.
 * 롤백되면 실행하지 않는다 (예: 탈퇴가 롤백됐는데 소셜 연결만 끊기는 일을 막는다).
 */
public final class AfterCommit {

    private AfterCommit() {
    }

    public static void run(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }
}
