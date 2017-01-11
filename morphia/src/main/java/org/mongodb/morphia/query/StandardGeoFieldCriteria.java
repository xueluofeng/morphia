package org.mongodb.morphia.query;

import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DBObject;
import org.mongodb.morphia.geo.CoordinateReferenceSystem;
import org.mongodb.morphia.geo.Geometry;
import org.mongodb.morphia.geo.GeometryQueryConverter;
import org.mongodb.morphia.geo.NamedCoordinateReferenceSystemConverter;
import org.mongodb.morphia.DatastoreImpl;

import static org.mongodb.morphia.query.FilterOperator.NEAR;

/**
 * Creates queries for GeoJson geo queries on MongoDB. These queries generally require MongoDB 2.4 and above, and usually work on 2d sphere
 * indexes.
 */
class StandardGeoFieldCriteria extends FieldCriteria {
    private final Integer maxDistanceMeters;
    private final DBObject geometryAsDBObject;
    private CoordinateReferenceSystem crs;

    StandardGeoFieldCriteria(final QueryImpl<?> query, final String field, final FilterOperator operator, final Geometry value,
                             final Integer maxDistanceMeters, final CoordinateReferenceSystem crs) {
        this(query, field, operator, value, maxDistanceMeters);
        this.crs = crs;
    }

    StandardGeoFieldCriteria(final QueryImpl<?> query, final String field, final FilterOperator operator, final Geometry value,
                             final Integer maxDistanceMeters) {
        super(query, field, operator, value);
        this.maxDistanceMeters = maxDistanceMeters;
        GeometryQueryConverter geometryQueryConverter = new GeometryQueryConverter(((DatastoreImpl) query.getDatastore()).getMapper());
        geometryAsDBObject = (DBObject) geometryQueryConverter.encode(value, null);
    }

    @Override
    public void addTo(final DBObject obj) {
        BasicDBObjectBuilder query;
        FilterOperator operator = getOperator();

        switch (operator) {
            case NEAR:
                if (maxDistanceMeters != null) {
                    geometryAsDBObject.put("$maxDistance", maxDistanceMeters);
                }
                query = BasicDBObjectBuilder.start(NEAR.val(), geometryAsDBObject);
                break;
            case GEO_WITHIN:
            case INTERSECTS:
                query = BasicDBObjectBuilder.start(operator.val(), geometryAsDBObject);
                if (crs != null) {
                    ((DBObject) geometryAsDBObject.get("$geometry")).put("crs", new NamedCoordinateReferenceSystemConverter().encode(crs));
                }
                break;
            default:
                throw new UnsupportedOperationException(String.format("Operator %s not supported for geo-query", operator.val()));
        }

        obj.put(getField(), query.get());
    }
}
