package xyz.morphia.geo;

import com.mongodb.client.model.geojson.Polygon;
import xyz.morphia.annotations.Id;
import xyz.morphia.annotations.Indexed;
import xyz.morphia.utils.IndexType;

public final class Area {
    @Id
    private String name;

    @Indexed(IndexType.GEO2DSPHERE)
    private Polygon area;

    Area() {
    }

    public Area(final String name, final Polygon area) {
        this.name = name;
        this.area = area;
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (area != null ? area.hashCode() : 0);
        return result;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Area area1 = (Area) o;

        if (area != null ? !area.equals(area1.area) : area1.area != null) {
            return false;
        }
        if (name != null ? !name.equals(area1.name) : area1.name != null) {
            return false;
        }

        return true;
    }

    @Override
    public String toString() {
        return "Area{"
               + "name='" + name + '\''
               + ", area=" + area
               + '}';
    }
}