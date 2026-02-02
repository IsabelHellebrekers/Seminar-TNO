package Solution;

/**
 * Presents a shipments decision from MSC -> FSC, MSC -> VUST or FSC -> OU. 
 * Quantity > 1 means that more trucks are needed, since one truck can carry at most
 * one CCL and can perform at most one round trip per day. 
 */
public record Shipment (
    String from, 
    String to, 
    String cclType,
    int day, 
    int quantity,
    String ouType
) {}
