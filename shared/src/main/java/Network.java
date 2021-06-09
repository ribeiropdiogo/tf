import com.google.protobuf.InvalidProtocolBufferException;
import io.atomix.cluster.messaging.ManagedMessagingService;
import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.cluster.messaging.impl.NettyMessagingService;
import io.atomix.utils.net.Address;
import protocol.Protocol.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

public class Network implements AutoCloseable {
    private final ManagedMessagingService messagingService;
    private final Executor executor;

    public Network(int localPort) {
        this.messagingService = new NettyMessagingService(
                "bank-cluster",
                Address.from("localhost", localPort),
                new MessagingConfig()
        );

        this.executor = Executors.newFixedThreadPool(1);
    }

    public void registerReplyHandler(String msgType, BiConsumer<Address, OperationReply> handler) {
        this.messagingService.registerHandler(
                msgType,
                (address, bytes) -> {
                    try {
                        handler.accept(address, OperationReply.parseFrom(bytes));
                    } catch (InvalidProtocolBufferException e) {
                        e.printStackTrace();
                    }
                },
                this.executor
        );
    }

    public void registerRequestHandler(String msgType, BiConsumer<Address, Operation> handler) {
        this.messagingService.registerHandler(
                msgType,
                (address, bytes) -> {
                    try {
                        handler.accept(address, Operation.parseFrom(bytes));
                    } catch (InvalidProtocolBufferException e) {
                        e.printStackTrace();
                    }
                },
                this.executor
        );
    }

    public CompletableFuture<Void> send(Address address, String msgType, Object payload) {
        byte[] array;

        if (payload instanceof  Operation){
            array = ((Operation) payload).toByteArray();
        } else{
            array = ((OperationReply) payload).toByteArray();
        }

        return this.messagingService.sendAsync(
                address,
                msgType,
                array
        );
    }

    public void start() throws ExecutionException, InterruptedException {
        this.messagingService.start().get();
    }

    @Override
    public void close() throws ExecutionException, InterruptedException {
        this.messagingService.stop().get();
    }
}