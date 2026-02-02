package Solution;

public record Shipment (
    String from, 
    String to, 
    String cclType,
    int day, 
    int quantity,
    String ouType
) {}
