package Visualisation.model;

/**
 * Enumeration of simulation event types used by the visualiser.
 *
 * @author 621349it Ies Timmerarends
 * @author 612348ih Isabel Hellebrekers
 * @author 631426ls Lena Stiebing
 * @author 661267eb Eeke Bavelaar
 */
public enum EventType {
    /** Daily demand consumption at all operating units. */
    DEMAND,
    /** Phase 1: FSC dispatches CCLs to its operating units. */
    FSC_DELIVERY,
    /** Phase 2: MSC replenishes FSCs and VUST. */
    DELIVERY
}
