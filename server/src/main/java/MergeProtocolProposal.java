public class MergeProtocolProposal implements Comparable<MergeProtocolProposal>{

    private final String PROCESS_ID;

    private final int NR_MESSAGES_APPLIED;

    private final int NR_MESSAGES_KNOWN;

    public MergeProtocolProposal(String processId, int nMessagesApplied, int nMessagesKnown){
        this.PROCESS_ID = processId;
        this.NR_MESSAGES_APPLIED = nMessagesApplied;
        this.NR_MESSAGES_KNOWN = nMessagesKnown;
    }

    public String getPROCESS_ID() {
        return PROCESS_ID;
    }

    public int getNR_MESSAGES_APPLIED() {
        return NR_MESSAGES_APPLIED;
    }

    public int getNR_MESSAGES_KNOWN(){
        return NR_MESSAGES_KNOWN;
    }

    @Override
    public int compareTo(MergeProtocolProposal o) {

        // Compare by the number of applied messages, if the number is equal
        // we need to compare the number of pending messages
        if (o.getNR_MESSAGES_APPLIED() == this.getNR_MESSAGES_APPLIED()){
            if (o.getNR_MESSAGES_KNOWN() == this.getNR_MESSAGES_KNOWN()){
                // Compare by process id
                return this.PROCESS_ID.compareTo(o.getPROCESS_ID());
            } else {
                // Since there is a draw in the number of applied messages compare
                // the number of messages pending to be applied
                return this.getNR_MESSAGES_KNOWN() > o.getNR_MESSAGES_KNOWN() ? -1 : 1;
            }
        } else {
            // If this has more messages processed than the other it means this
            // is more likely to be leader
            return this.getNR_MESSAGES_APPLIED() > o.getNR_MESSAGES_APPLIED() ? -1 : 1;
        }
    }
}
