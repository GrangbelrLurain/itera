package aerocore.gds.client;

import aerocore.gds.model.GdsPnrRequest;
import aerocore.gds.model.GdsPnrResponse;
import aerocore.gds.model.GdsTicketCommand;
import aerocore.gds.model.GdsTicketResult;
import aerocore.gds.model.GdsType;

/**
 * Common GDS Client Interface for low-level SDK communication.
 */
public interface GdsClient {
    GdsType getGdsType();
    GdsPnrResponse createPnr(GdsPnrRequest request) throws Exception;
    GdsTicketResult issueTicket(GdsTicketCommand command) throws Exception;
    boolean cancelPnr(String pnrCode) throws Exception;
}
