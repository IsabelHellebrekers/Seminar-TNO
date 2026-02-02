package Objects;

/**
 * Presents a Main Supply Centre (MSC) of Forward Supply Centre (FSC).
 */
public class Centre {
    public final String centre;     
    public final Integer maxStorageCapCcls; 
    public final String source;      
    public final java.time.LocalTime orderTime; 
    public final String timeWindow;  
    public final Integer drivingTimeToSourceSec; 

    /**
     * Constructor.
     * @param centre                    centre name
     * @param maxStorageCapCcls         max storage in number of CCLs
     * @param source                    source centre
     * @param orderTime                 order time  
     * @param timeWindow                time window to deliver supplies
     * @param drivingTimeToSourceSec    driving time to source in seconds
     */
    public Centre(String centre, Integer maxStorageCapCcls, String source,
                  java.time.LocalTime orderTime, String timeWindow, Integer drivingTimeToSourceSec) {
        this.centre = centre;
        this.maxStorageCapCcls = maxStorageCapCcls;
        this.source = source;
        this.orderTime = orderTime;
        this.timeWindow = timeWindow;
        this.drivingTimeToSourceSec = drivingTimeToSourceSec;
    }

    @Override 
    public String toString() {
        return "centre='" + centre + '\'' +
                ", maxStorageCapCcls=" + maxStorageCapCcls +
                ", source='" + source + '\'' +
                ", orderTime=" + orderTime +
                ", timeWindow='" + timeWindow + '\'' +
                ", drivingTimeSec=" + drivingTimeToSourceSec +
                '}';
    }
}
