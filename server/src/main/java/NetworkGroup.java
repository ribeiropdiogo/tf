import protocol.Protocol.*;
import spread.*;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

public class NetworkGroup implements AutoCloseable {

    private final SpreadConnection connection;
    private final Map<String, SpreadGroup> groups;

    public NetworkGroup(int localPort, String processName, boolean subscribeToMembership) throws UnknownHostException, SpreadException {
        this.connection = new SpreadConnection();

        this.connection.connect(InetAddress.getByName("localhost"), localPort, processName, false, subscribeToMembership);

        this.groups = new HashMap<>();
    }

    public void addBasicListener(BasicMessageListener basicMessageListener) {
        this.connection.add(basicMessageListener);
    }

    public void addAdvancedListener(AdvancedMessageListener advancedMessageListener){
        this.connection.add(advancedMessageListener);
    }

    public void sendSafe(String group, Object payload) throws SpreadException {
        SpreadMessage message = new SpreadMessage();

        byte[] array;

        if (payload instanceof  Operation){
            array = ((Operation) payload).toByteArray();
        } else{
            array = ((OperationReply) payload).toByteArray();
        }

        message.setData(array);
        message.setSafe();
        message.addGroup(group);

        connection.multicast(message);
    }

    public void sendReliable(String group, Object payload) throws SpreadException {
        SpreadMessage message = new SpreadMessage();

        byte[] array;

        if (payload instanceof  Operation){
            array = ((Operation) payload).toByteArray();
        } else{
            array = ((OperationReply) payload).toByteArray();
        }

        message.setData(array);
        message.setReliable(); // No agreement for a single destination
        message.addGroup(group);

        connection.multicast(message);
    }

    public void joinGroup(String groupName) throws SpreadException {
        SpreadGroup group = new SpreadGroup();
        group.join(connection, groupName);
        groups.put(groupName, group);
    }

    public boolean leaveGroup(String groupName) throws SpreadException {
        if (groups.containsKey(groupName)){
            SpreadGroup group = groups.get(groupName);
            group.leave();
            groups.remove(groupName);
            return true;
        }else{
            return false;
        }
    }

    @Override
    public void close() throws SpreadException {
        this.connection.disconnect();
    }
}