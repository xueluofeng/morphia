/*
 * Copyright (C) 2010 Olafur Gauti Gudmundsson
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package org.mongodb.morphia;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.WriteConcern;
import org.bson.types.ObjectId;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.mongodb.morphia.annotations.Embedded;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.Indexed;
import org.mongodb.morphia.annotations.PreLoad;
import org.mongodb.morphia.logging.Logger;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.TestQuery.ContainsPic;
import org.mongodb.morphia.query.TestQuery.Pic;
import org.mongodb.morphia.query.UpdateOperations;
import org.mongodb.morphia.query.UpdateOpsImpl;
import org.mongodb.morphia.query.ValidationException;
import org.mongodb.morphia.testmodel.Article;
import org.mongodb.morphia.testmodel.Circle;
import org.mongodb.morphia.testmodel.Rectangle;
import org.mongodb.morphia.testmodel.Translation;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mongodb.morphia.logging.MorphiaLoggerFactory.get;
import static org.mongodb.morphia.query.PushOptions.options;

@SuppressWarnings("UnusedDeclaration")
public class TestUpdateOps extends TestBase {
    private static final Logger LOG = get(TestUpdateOps.class);

    @Test
    public void shouldUpdateAnArrayElement() {
        // given
        ObjectId parentId = new ObjectId();
        String childName = "Bob";
        String updatedLastName = "updatedLastName";

        Parent parent = new Parent();
        parent.id = parentId;
        parent.children.add(new Child("Anthony", "Child"));
        parent.children.add(new Child(childName, "originalLastName"));
        getDatastore().save(parent);

        // when
        Query<Parent> query = getDatastore().find(Parent.class)
                                            .field("_id").equal(parentId)
                                            .field("children.first")
                                            .equal(childName);
        UpdateOperations<Parent> updateOps = getDatastore().createUpdateOperations(Parent.class)
                                                           .set("children.$.last", updatedLastName);
        UpdateResults updateResults = getDatastore().update(query, updateOps);

        // then
        assertThat(updateResults.getUpdatedCount(), is(1));
        assertThat(getDatastore().find(Parent.class).filter("id", parentId).get().children, hasItem(new Child(childName, updatedLastName)));
    }

    @Test
    public void testDisableValidation() {
        Child child1 = new Child("James", "Rigney");

        validateClassName("children", getDatastore().createUpdateOperations(Parent.class)
                                                    .removeAll("children", child1), false);

        validateClassName("children", getDatastore().createUpdateOperations(Parent.class)
                                                    .disableValidation()
                                                    .removeAll("children", child1), false);

        validateClassName("c", getDatastore().createUpdateOperations(Parent.class)
                                             .disableValidation()
                                             .removeAll("c", child1), true);
    }

    private void validateClassName(final String path, final UpdateOperations<Parent> ops, final boolean expected) {
        DBObject ops1 = ((UpdateOpsImpl) ops).getOperations();
        Map pull = (Map) ops1.get("$pull");
        Map children = (Map) pull.get(path);
        Assert.assertEquals(expected, children.containsKey("className"));
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testAdd() {
        checkMinServerVersion(2.6);

        ContainsIntArray cIntArray = new ContainsIntArray();
        Datastore ds = getDatastore();
        ds.save(cIntArray);

        assertThat(ds.get(cIntArray).values, is((new ContainsIntArray()).values));

        //add 4 to array
        assertUpdated(ds.update(ds.createQuery(ContainsIntArray.class),
                                     ds.createUpdateOperations(ContainsIntArray.class)
                                            .add("values", 4, false)),
                      1);

        assertThat(ds.get(cIntArray).values, is(new Integer[]{1, 2, 3, 4}));

        //add unique (4) -- noop
        assertUpdated(ds.update(ds.createQuery(ContainsIntArray.class),
                                     ds.createUpdateOperations(ContainsIntArray.class)
                                            .add("values", 4, false)),
                      1);
        assertThat(ds.get(cIntArray).values, is(new Integer[]{1, 2, 3, 4}));

        //add dup 4
        assertUpdated(ds.update(ds.createQuery(ContainsIntArray.class),
                                     ds.createUpdateOperations(ContainsIntArray.class)
                                            .add("values", 4, true)),
                      1);
        assertThat(ds.get(cIntArray).values, is(new Integer[]{1, 2, 3, 4, 4}));

        //cleanup for next tests
        ds.delete(ds.find(ContainsIntArray.class));
        cIntArray = ds.getByKey(ContainsIntArray.class, ds.save(new ContainsIntArray()));

        //add [4,5]
        final List<Integer> newValues = new ArrayList<Integer>();
        newValues.add(4);
        newValues.add(5);
        assertUpdated(ds.update(ds.createQuery(ContainsIntArray.class),
                                     ds.createUpdateOperations(ContainsIntArray.class)
                                            .addAll("values", newValues, false)),
                      1);
        assertThat(ds.get(cIntArray).values, is(new Integer[]{1, 2, 3, 4, 5}));

        //add them again... noop
        assertUpdated(ds.update(ds.createQuery(ContainsIntArray.class),
                                     ds.createUpdateOperations(ContainsIntArray.class)
                                            .addAll("values", newValues, false)),
                      1);
        assertThat(ds.get(cIntArray).values, is(new Integer[]{1, 2, 3, 4, 5}));

        //add dups [4,5]
        assertUpdated(ds.update(ds.createQuery(ContainsIntArray.class),
                                ds.createUpdateOperations(ContainsIntArray.class)
                                  .addAll("values", newValues, true)),
                      1);
        assertThat(ds.get(cIntArray).values, is(new Integer[]{1, 2, 3, 4, 5, 4, 5}));
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testAddAll() {
        getMorphia().map(EntityLogs.class, EntityLog.class);
        String uuid = "4ec6ada9-081a-424f-bee0-934c0bc4fab7";

        EntityLogs logs = new EntityLogs();
        logs.uuid = uuid;
        getDatastore().save(logs);

        Query<EntityLogs> finder = getDatastore().find(EntityLogs.class).field("uuid").equal(uuid);

        // both of these entries will have a className attribute
        List<EntityLog> latestLogs = asList(new EntityLog("whatever1", new Date()), new EntityLog("whatever2", new Date()));
        UpdateOperations<EntityLogs> updateOperationsAll = getDatastore().createUpdateOperations(EntityLogs.class)
                                                                         .addAll("logs", latestLogs, false);
        getDatastore().update(finder, updateOperationsAll, true);
        validateNoClassName(finder.get());

        // this entry will NOT have a className attribute
        UpdateOperations<EntityLogs> updateOperations3 = getDatastore().createUpdateOperations(EntityLogs.class)
                                                                       .add("logs", new EntityLog("whatever3", new Date()), false);
        getDatastore().update(finder, updateOperations3, true);
        validateNoClassName(finder.get());

        // this entry will NOT have a className attribute
        UpdateOperations<EntityLogs> updateOperations4 = getDatastore().createUpdateOperations(EntityLogs.class)
                                                                       .add("logs", new EntityLog("whatever4", new Date()), false);
        getDatastore().update(finder, updateOperations4, true);
        validateNoClassName(finder.get());
    }

    @Test
    public void testAddToSet() {
        ContainsIntArray cIntArray = new ContainsIntArray();
        getDatastore().save(cIntArray);

        assertThat(getDatastore().get(cIntArray).values, is((new ContainsIntArray()).values));

        assertUpdated(getDatastore().update(getDatastore().find(ContainsIntArray.class),
                                     getDatastore().createUpdateOperations(ContainsIntArray.class)
                                                   .addToSet("values", 5)),
                      1);
        assertThat(getDatastore().get(cIntArray).values, is(new Integer[]{1, 2, 3, 5}));

        assertUpdated(getDatastore().update(getDatastore().find(ContainsIntArray.class),
                                     getDatastore().createUpdateOperations(ContainsIntArray.class)
                                                   .addToSet("values", 4)),
                      1);
        assertThat(getDatastore().get(cIntArray).values, is(new Integer[]{1, 2, 3, 5, 4}));

        assertUpdated(getDatastore().update(getDatastore().find(ContainsIntArray.class),
                                     getDatastore().createUpdateOperations(ContainsIntArray.class)
                                                   .addToSet("values", asList(8, 9))),
                      1);
        assertThat(getDatastore().get(cIntArray).values, is(new Integer[]{1, 2, 3, 5, 4, 8, 9}));

        assertUpdated(getDatastore().update(getDatastore().find(ContainsIntArray.class),
                                     getDatastore().createUpdateOperations(ContainsIntArray.class)
                                                   .addToSet("values", asList(4, 5))),
                      1);
        assertThat(getDatastore().get(cIntArray).values, is(new Integer[]{1, 2, 3, 5, 4, 8, 9}));
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testUpdateFirst() {
        ContainsIntArray cIntArray = new ContainsIntArray();
        ContainsIntArray control = new ContainsIntArray();
        Datastore ds = getDatastore();
        ds.save(cIntArray, control);

        assertThat(ds.get(cIntArray).values, is((new ContainsIntArray()).values));
        Query<ContainsIntArray> query = ds.find(ContainsIntArray.class);

        doUpdates(cIntArray, control, query, ds.createUpdateOperations(ContainsIntArray.class)
                                                    .addToSet("values", 4),
                  new Integer[]{1, 2, 3, 4});


        doUpdates(cIntArray, control, query, ds.createUpdateOperations(ContainsIntArray.class)
                                                    .addToSet("values", asList(4, 5)),
                  new Integer[]{1, 2, 3, 4, 5});


        assertInserted(ds.updateFirst(ds.find(ContainsIntArray.class)
                                       .filter("values", new Integer[]{4, 5, 7}),
                                     ds.createUpdateOperations(ContainsIntArray.class)
                                       .addToSet("values", 6), true));
        assertNotNull(ds.find(ContainsIntArray.class)
                        .filter("values", new Integer[]{4, 5, 7, 6}));
    }

    @SuppressWarnings("deprecation")
    private void doUpdates(final ContainsIntArray updated, final ContainsIntArray control,
                           final Query<ContainsIntArray> query, final UpdateOperations<ContainsIntArray> operations,
                           final Integer[] target) {
        assertUpdated(getDatastore().updateFirst(query, operations), 1);
        assertThat(getDatastore().get(updated).values, is(target));
        assertThat(getDatastore().get(control).values, is(new Integer[]{1, 2, 3}));

        assertUpdated(getDatastore().update(query, operations, new UpdateOptions()), 1);
        assertThat(getDatastore().get(updated).values, is(target));
        assertThat(getDatastore().get(control).values, is(new Integer[]{1, 2, 3}));
    }

    @Test
    public void testExistingUpdates() {
        Circle c = new Circle(100D);
        getDatastore().save(c);
        c = new Circle(12D);
        getDatastore().save(c);
        assertUpdated(getDatastore().update(getDatastore().find(Circle.class),
                                     getDatastore().createUpdateOperations(Circle.class)
                                                   .inc("radius", 1D),
                                     new UpdateOptions()),
                      1);

        assertUpdated(getDatastore().update(getDatastore().find(Circle.class),
                                     getDatastore().createUpdateOperations(Circle.class)
                                                   .inc("radius")),
                      2);

        //test possible data type change.
        final Circle updatedCircle = getDatastore().find(Circle.class).filter("radius", 13).get();
        assertThat(updatedCircle, is(notNullValue()));
        assertThat(updatedCircle.getRadius(), is(13D));
    }

    @Test
    public void testIncDec() {
        final Rectangle[] array = {new Rectangle(1, 10), new Rectangle(1, 10), new Rectangle(1, 10), new Rectangle(10, 10),
            new Rectangle(10, 10)};

        for (final Rectangle rect : array) {
            getDatastore().save(rect);
        }

        final Query<Rectangle> heightOf1 = getDatastore().find(Rectangle.class).filter("height", 1D);
        final Query<Rectangle> heightOf2 = getDatastore().find(Rectangle.class).filter("height", 2D);
        final Query<Rectangle> heightOf35 = getDatastore().find(Rectangle.class).filter("height", 3.5D);

        assertThat(getDatastore().getCount(heightOf1), is(3L));
        assertThat(getDatastore().getCount(heightOf2), is(0L));

        final UpdateResults results = getDatastore().update(heightOf1, getDatastore().createUpdateOperations(Rectangle.class)
                                                                                     .inc("height"));
        assertUpdated(results, 3);

        assertThat(getDatastore().getCount(heightOf1), is(0L));
        assertThat(getDatastore().getCount(heightOf2), is(3L));

        getDatastore().update(heightOf2, getDatastore().createUpdateOperations(Rectangle.class).dec("height"));
        assertThat(getDatastore().getCount(heightOf1), is(3L));
        assertThat(getDatastore().getCount(heightOf2), is(0L));

        getDatastore().update(heightOf1, getDatastore().createUpdateOperations(Rectangle.class).inc("height", 2.5D));
        assertThat(getDatastore().getCount(heightOf1), is(0L));
        assertThat(getDatastore().getCount(heightOf35), is(3L));

        getDatastore().update(heightOf35, getDatastore().createUpdateOperations(Rectangle.class).dec("height", 2.5D));
        assertThat(getDatastore().getCount(heightOf1), is(3L));
        assertThat(getDatastore().getCount(heightOf35), is(0L));

        getDatastore().update(getDatastore().find(Rectangle.class).filter("height", 1D),
                       getDatastore().createUpdateOperations(Rectangle.class)
                                     .set("height", 1D)
                                     .inc("width", 20D));

        assertThat(getDatastore().getCount(Rectangle.class), is(5L));
        assertThat(getDatastore().find(Rectangle.class).filter("height", 1D).get(), is(notNullValue()));
        assertThat(getDatastore().find(Rectangle.class).filter("width", 30D).get(), is(notNullValue()));

        getDatastore().update(getDatastore().find(Rectangle.class).filter("width", 30D),
                       getDatastore().createUpdateOperations(Rectangle.class).set("height", 2D).set("width", 2D));
        assertThat(getDatastore().find(Rectangle.class).filter("width", 1D).get(), is(nullValue()));
        assertThat(getDatastore().find(Rectangle.class).filter("width", 2D).get(), is(notNullValue()));

        getDatastore().update(heightOf35, getDatastore().createUpdateOperations(Rectangle.class).dec("height", 1));
        getDatastore().update(heightOf35, getDatastore().createUpdateOperations(Rectangle.class).dec("height", Long.MAX_VALUE));
        getDatastore().update(heightOf35, getDatastore().createUpdateOperations(Rectangle.class).dec("height", 1.5f));
        getDatastore().update(heightOf35, getDatastore().createUpdateOperations(Rectangle.class).dec("height", Double.MAX_VALUE));
        try {
            getDatastore().update(heightOf35, getDatastore().createUpdateOperations(Rectangle.class)
                                                            .dec("height", new AtomicInteger(1)));
            fail("Wrong data type not recognized.");
        } catch (IllegalArgumentException ignore) {}
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testInsertUpdate() {
        assertInserted(getDatastore().update(getDatastore().find(Circle.class).field("radius").equal(0),
                                      getDatastore().createUpdateOperations(Circle.class).inc("radius", 1D), true));
        assertInserted(getDatastore().update(getDatastore().find(Circle.class).field("radius").equal(0),
                                      getDatastore().createUpdateOperations(Circle.class).inc("radius", 1D),
                                      new UpdateOptions()
                                          .upsert(true)));
    }

    @Test
    public void testInsertWithRef() {
        final Pic pic = new Pic();
        pic.setName("fist");
        final Key<Pic> picKey = getDatastore().save(pic);

        assertInserted(getDatastore().update(getDatastore().find(ContainsPic.class).filter("name", "first").filter("pic", picKey),
                                           getDatastore().createUpdateOperations(ContainsPic.class)
                                                         .set("name", "A"),
                                           new UpdateOptions().upsert(true)));
        assertThat(getDatastore().find(ContainsPic.class).count(), is(1L));
        getDatastore().delete(getDatastore().find(ContainsPic.class));

        assertInserted(getDatastore().update(getDatastore().find(ContainsPic.class).filter("name", "first").filter("pic", pic),
                                           getDatastore().createUpdateOperations(ContainsPic.class).set("name", "second"),
                                      new UpdateOptions()
                                          .upsert(true)));
        assertThat(getDatastore().find(ContainsPic.class).count(), is(1L));

        //test reading the object.
        final ContainsPic cp = getDatastore().find(ContainsPic.class).get();
        assertThat(cp, is(notNullValue()));
        assertThat(cp.getName(), is("second"));
        assertThat(cp.getPic(), is(notNullValue()));
        assertThat(cp.getPic().getName(), is(notNullValue()));
        assertThat(cp.getPic().getName(), is("fist"));
    }

    @Test
    public void testMaxKeepsCurrentDocumentValueWhenThisIsLargerThanSuppliedValue() {
        checkMinServerVersion(2.6);
        final ObjectId id = new ObjectId();
        final double originalValue = 2D;

        Datastore ds = getDatastore();
        assertInserted(ds.update(ds.find(Circle.class)
                                   .field("id").equal(id),
                                 ds.createUpdateOperations(Circle.class)
                                   .setOnInsert("radius", originalValue),
                                 new UpdateOptions()
                                     .upsert(true)));

        assertUpdated(ds.update(ds.find(Circle.class)
                                  .field("id").equal(id),
                                ds.createUpdateOperations(Circle.class)
                                  .max("radius", 1D),
                                new UpdateOptions()
                                    .upsert(true)),
                      1);


        assertThat(ds.get(Circle.class, id).getRadius(), is(originalValue));
    }

    @Test
    public void testMinKeepsCurrentDocumentValueWhenThisIsSmallerThanSuppliedValue() {
        checkMinServerVersion(2.6);
        final ObjectId id = new ObjectId();
        final double originalValue = 3D;

        assertInserted(getDatastore().update(getDatastore().find(Circle.class).field("id").equal(id),
                                      getDatastore().createUpdateOperations(Circle.class).setOnInsert("radius", originalValue),
                                      new UpdateOptions().upsert(true)));

        assertUpdated(getDatastore().update(getDatastore().find(Circle.class).field("id").equal(id),
                                     getDatastore().createUpdateOperations(Circle.class).min("radius", 5D),
                                     new UpdateOptions().upsert(true)), 1);

        final Circle updatedCircle = getDatastore().get(Circle.class, id);
        assertThat(updatedCircle, is(notNullValue()));
        assertThat(updatedCircle.getRadius(), is(originalValue));
    }

    @Test
    public void testMinUsesSuppliedValueWhenThisIsSmallerThanCurrentDocumentValue() {
        checkMinServerVersion(2.6);
        final ObjectId id = new ObjectId();
        final double newLowerValue = 2D;

        assertInserted(getDatastore().update(getDatastore().find(Circle.class).field("id").equal(id),
                                      getDatastore().createUpdateOperations(Circle.class).setOnInsert("radius", 3D),
                                      new UpdateOptions().upsert(true)));


        assertUpdated(getDatastore().update(getDatastore().find(Circle.class).field("id").equal(id),
                                     getDatastore().createUpdateOperations(Circle.class).min("radius", newLowerValue),
                                     new UpdateOptions().upsert(true)), 1);

        final Circle updatedCircle = getDatastore().get(Circle.class, id);
        assertThat(updatedCircle, is(notNullValue()));
        assertThat(updatedCircle.getRadius(), is(newLowerValue));
    }

    @Test
    public void testPush() {
        checkMinServerVersion(2.6);
        ContainsIntArray cIntArray = new ContainsIntArray();
        getDatastore().save(cIntArray);
        assertThat(getDatastore().get(cIntArray).values, is((new ContainsIntArray()).values));

        getDatastore().update(getDatastore().find(ContainsIntArray.class),
                       getDatastore().createUpdateOperations(ContainsIntArray.class)
                                     .push("values", 4),
                       new UpdateOptions()
                           .multi(false));

        assertThat(getDatastore().get(cIntArray).values, is(new Integer[]{1, 2, 3, 4}));

        getDatastore().update(getDatastore().find(ContainsIntArray.class),
                       getDatastore().createUpdateOperations(ContainsIntArray.class)
                                     .push("values", 4),
                       new UpdateOptions()
                           .multi(false));

        assertThat(getDatastore().get(cIntArray).values, is(new Integer[]{1, 2, 3, 4, 4}));

        getDatastore().update(getDatastore().find(ContainsIntArray.class),
                       getDatastore().createUpdateOperations(ContainsIntArray.class)
                                     .push("values", asList(5, 6)),
                       new UpdateOptions()
                           .multi(false));

        assertThat(getDatastore().get(cIntArray).values, is(new Integer[]{1, 2, 3, 4, 4, 5, 6}));

        getDatastore().update(getDatastore().find(ContainsIntArray.class),
                       getDatastore().createUpdateOperations(ContainsIntArray.class)
                                     .push("values", 12, options().position(2)),
                       new UpdateOptions()
                           .multi(false));

        assertThat(getDatastore().get(cIntArray).values, is(new Integer[]{1, 2, 12, 3, 4, 4, 5, 6}));


        getDatastore().update(getDatastore().find(ContainsIntArray.class),
                       getDatastore().createUpdateOperations(ContainsIntArray.class)
                                     .push("values", asList(99, 98, 97), options().position(4)),
                       new UpdateOptions()
                           .multi(false));

        assertThat(getDatastore().get(cIntArray).values, is(new Integer[]{1, 2, 12, 3, 99, 98, 97, 4, 4, 5, 6}));
    }

    @Test
    public void testRemoveAllSingleValue() {
        EntityLogs logs = new EntityLogs();
        Date date = new Date();
        logs.logs.addAll(asList(
            new EntityLog("log1", date),
            new EntityLog("log2", date),
            new EntityLog("log3", date),
            new EntityLog("log1", date),
            new EntityLog("log2", date),
            new EntityLog("log3", date)));

        Datastore ds = getDatastore();
        ds.save(logs);

        UpdateOperations<EntityLogs> operations =
            ds.createUpdateOperations(EntityLogs.class).removeAll("logs", new EntityLog("log3", date));

        UpdateResults results = ds.update(ds.find(EntityLogs.class), operations);
        Assert.assertEquals(1, results.getUpdatedCount());
        EntityLogs updated = ds.find(EntityLogs.class).get();
        Assert.assertEquals(4, updated.logs.size());
        for (int i = 0; i < 4; i++) {
            Assert.assertEquals(new EntityLog("log" + ((i % 2) + 1), date), updated.logs.get(i));
        }
    }

    @Test
    public void testRemoveAllList() {
        EntityLogs logs = new EntityLogs();
        Date date = new Date();
        logs.logs.addAll(asList(
            new EntityLog("log1", date),
            new EntityLog("log2", date),
            new EntityLog("log3", date),
            new EntityLog("log1", date),
            new EntityLog("log2", date),
            new EntityLog("log3", date)));

        Datastore ds = getDatastore();
        ds.save(logs);

        UpdateOperations<EntityLogs> operations =
            ds.createUpdateOperations(EntityLogs.class).removeAll("logs", singletonList(new EntityLog("log3", date)));

        UpdateResults results = ds.update(ds.find(EntityLogs.class), operations);
        Assert.assertEquals(1, results.getUpdatedCount());
        EntityLogs updated = ds.find(EntityLogs.class).get();
        Assert.assertEquals(4, updated.logs.size());
        for (int i = 0; i < 4; i++) {
            Assert.assertEquals(new EntityLog("log" + ((i % 2) + 1), date), updated.logs.get(i));
        }
    }

    @Test
    @Ignore("mapping in WriteResult needs to be resolved")
    public void testRemoveWithNoData() {
        DumbColl dumbColl = new DumbColl("ID");
        dumbColl.fromArray = singletonList(new DumbArrayElement("something"));
        DumbColl dumbColl2 = new DumbColl("ID2");
        dumbColl2.fromArray = singletonList(new DumbArrayElement("something"));
        getDatastore().save(asList(dumbColl, dumbColl2));

        UpdateResults deleteResults = getDatastore().update(
            getDatastore().find(DumbColl.class).field("opaqueId").equalIgnoreCase("ID"),
            getAds().createUpdateOperations(DumbColl.class,
                new BasicDBObject("$pull", new BasicDBObject("fromArray", new BasicDBObject("whereId", "not there")))));

        getDatastore().update(
            getDatastore().find(DumbColl.class).field("opaqueId").equalIgnoreCase("ID"),
            getAds().createUpdateOperations(DumbColl.class)
                .removeAll("fromArray", new DumbArrayElement("something")));
    }

    @Test
    public void testElemMatchUpdate() {
        // setUp
        Object id = getDatastore().save(new ContainsIntArray()).getId();
        assertThat(getDatastore().get(ContainsIntArray.class, id).values, arrayContaining(1, 2, 3));

        // do patch
        Query<ContainsIntArray> q = getDatastore().createQuery(ContainsIntArray.class)
                                                  .filter("id", id)
                                                  .filter("values", 2);

        UpdateOperations<ContainsIntArray> ops = getDatastore().createUpdateOperations(ContainsIntArray.class)
                                                               .set("values.$", 5);
        getDatastore().update(q, ops);

        // expected
        assertThat(getDatastore().get(ContainsIntArray.class, id).values, arrayContaining(1, 5, 3));
    }

    @Test
    public void testRemoveFirst() {
        final ContainsIntArray cIntArray = new ContainsIntArray();
        getDatastore().save(cIntArray);
        ContainsIntArray cIALoaded = getDatastore().get(cIntArray);
        assertThat(cIALoaded.values.length, is(3));
        assertThat(cIALoaded.values, is((new ContainsIntArray()).values));

        assertUpdated(getDatastore().update(getDatastore().find(ContainsIntArray.class),
                                     getDatastore().createUpdateOperations(ContainsIntArray.class)
                                                   .removeFirst("values"),
                                     new UpdateOptions()
                                         .multi(false)),
                      1);
        assertThat(getDatastore().get(cIntArray).values, is(new Integer[]{2, 3}));

        assertUpdated(getDatastore().update(getDatastore().find(ContainsIntArray.class),
                                     getDatastore().createUpdateOperations(ContainsIntArray.class)
                                                   .removeLast("values"),
                                     new UpdateOptions()
                                         .multi(false)),
                      1);
        assertThat(getDatastore().get(cIntArray).values, is(new Integer[]{2}));
    }

    @Test
    public void testSetOnInsertWhenInserting() {
        checkMinServerVersion(2.4);
        ObjectId id = new ObjectId();

        assertInserted(getDatastore().update(getDatastore().find(Circle.class).field("id").equal(id),
                                      getDatastore().createUpdateOperations(Circle.class).setOnInsert("radius", 2D),
                                      new UpdateOptions()
                                          .upsert(true)));

        final Circle updatedCircle = getDatastore().get(Circle.class, id);

        assertThat(updatedCircle, is(notNullValue()));
        assertThat(updatedCircle.getRadius(), is(2D));
    }

    @Test
    public void testSetOnInsertWhenUpdating() {
        checkMinServerVersion(2.4);
        ObjectId id = new ObjectId();

        assertInserted(getDatastore().update(getDatastore().find(Circle.class).field("id").equal(id),
                                      getDatastore().createUpdateOperations(Circle.class).setOnInsert("radius", 1D),
                                      new UpdateOptions()
                                          .upsert(true)));

        assertUpdated(getDatastore().update(getDatastore().find(Circle.class).field("id").equal(id),
                                     getDatastore().createUpdateOperations(Circle.class).setOnInsert("radius", 2D),
                                     new UpdateOptions()
                                         .upsert(true)),
                      1);

        final Circle updatedCircle = getDatastore().get(Circle.class, id);

        assertThat(updatedCircle, is(notNullValue()));
        assertThat(updatedCircle.getRadius(), is(1D));
    }

    @Test
    public void testSetUnset() {
        Datastore ds = getDatastore();
        final Key<Circle> key = ds.save(new Circle(1));

        assertUpdated(ds.update(ds.find(Circle.class).filter("radius", 1D),
                                ds.createUpdateOperations(Circle.class).set("radius", 2D),
                                new UpdateOptions()
                                    .multi(false)),
                      1);

        assertThat(ds.getByKey(Circle.class, key).getRadius(), is(2D));


        assertUpdated(ds.update(ds.find(Circle.class).filter("radius", 2D),
                                ds.createUpdateOperations(Circle.class).unset("radius"),
                                new UpdateOptions()
                                    .multi(false)),
                      1);

        assertThat(ds.getByKey(Circle.class, key).getRadius(), is(0D));

        Article article = new Article();

        ds.save(article);

        ds.update(ds.find(Article.class),
                  ds.createUpdateOperations(Article.class)
                 .set("translations", new HashMap<String, Translation>()));

        ds.update(ds.find(Article.class),
                  ds.createUpdateOperations(Article.class)
                 .unset("translations"));
    }

    @Test
    public void testUpdateFirstNoCreate() {
        getDatastore().delete(getDatastore().find(EntityLogs.class));
        List<EntityLogs> logs = new ArrayList<EntityLogs>();
        for (int i = 0; i < 100; i++) {
            logs.add(createEntryLogs("name", "logs" + i));
        }
        EntityLogs logs1 = logs.get(0);
        Query<EntityLogs> query = getDatastore().find(EntityLogs.class);
        UpdateOperations<EntityLogs> updateOperations = getDatastore().createUpdateOperations(EntityLogs.class);
        BasicDBObject object = new BasicDBObject("new", "value");
        updateOperations.set("raw", object);

        getDatastore().update(query, updateOperations, new UpdateOptions());

        List<EntityLogs> list = getDatastore().find(EntityLogs.class).asList();
        for (int i = 0; i < list.size(); i++) {
            final EntityLogs entityLogs = list.get(i);
            assertEquals(entityLogs.id.equals(logs1.id) ? object : logs.get(i).raw, entityLogs.raw);
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testUpdateFirstNoCreateWithEntity() {
        List<EntityLogs> logs = new ArrayList<EntityLogs>();
        for (int i = 0; i < 100; i++) {
            logs.add(createEntryLogs("name", "logs" + i));
        }
        EntityLogs logs1 = logs.get(0);

        Query<EntityLogs> query = getDatastore().find(EntityLogs.class);
        BasicDBObject object = new BasicDBObject("new", "value");
        EntityLogs newLogs = new EntityLogs();
        newLogs.raw = object;

        getDatastore().updateFirst(query, newLogs, false);

        List<EntityLogs> list = getDatastore().find(EntityLogs.class).asList();
        for (int i = 0; i < list.size(); i++) {
            final EntityLogs entityLogs = list.get(i);
            assertEquals(entityLogs.id.equals(logs1.id) ? object : logs.get(i).raw, entityLogs.raw);
        }
    }

    @Test
    public void testUpdateFirstNoCreateWithWriteConcern() {
        List<EntityLogs> logs = new ArrayList<EntityLogs>();
        for (int i = 0; i < 100; i++) {
            logs.add(createEntryLogs("name", "logs" + i));
        }
        EntityLogs logs1 = logs.get(0);

        getDatastore().update(getDatastore().find(EntityLogs.class),
                       getDatastore().createUpdateOperations(EntityLogs.class)
                                     .set("raw", new BasicDBObject("new", "value")),
                       new UpdateOptions());

        List<EntityLogs> list = getDatastore().find(EntityLogs.class).asList();
        for (int i = 0; i < list.size(); i++) {
            final EntityLogs entityLogs = list.get(i);
            assertEquals(entityLogs.id.equals(logs1.id) ? new BasicDBObject("new", "value") : logs.get(i).raw, entityLogs.raw);
        }
    }

    @Test
    public void testUpdateKeyRef() {
        final ContainsPicKey cpk = new ContainsPicKey();
        cpk.name = "cpk one";

        Datastore ds = getDatastore();
        ds.save(cpk);

        final Pic pic = new Pic();
        pic.setName("fist again");
        final Key<Pic> picKey = ds.save(pic);
        // picKey = getDs().getKey(pic);


        //test with Key<Pic>

        assertThat(ds.update(ds.find(ContainsPicKey.class).filter("name", cpk.name),
                             ds.createUpdateOperations(ContainsPicKey.class).set("pic", pic),
                             new UpdateOptions()).getUpdatedCount(),
                   is(1));

        //test reading the object.
        final ContainsPicKey cpk2 = ds.find(ContainsPicKey.class).get();
        assertThat(cpk2, is(notNullValue()));
        assertThat(cpk.name, is(cpk2.name));
        assertThat(cpk2.pic, is(notNullValue()));
        assertThat(picKey, is(cpk2.pic));

        ds.update(ds.find(ContainsPicKey.class).filter("name", cpk.name),
                  ds.createUpdateOperations(ContainsPicKey.class).set("pic", picKey),
                  new UpdateOptions());

        //test reading the object.
        final ContainsPicKey cpk3 = ds.find(ContainsPicKey.class).get();
        assertThat(cpk3, is(notNullValue()));
        assertThat(cpk.name, is(cpk3.name));
        assertThat(cpk3.pic, is(notNullValue()));
        assertThat(picKey, is(cpk3.pic));
    }

    @Test
    public void testUpdateKeyList() {
        final ContainsPicKey cpk = new ContainsPicKey();
        cpk.name = "cpk one";

        Datastore ds = getDatastore();
        ds.save(cpk);

        final Pic pic = new Pic();
        pic.setName("fist again");
        final Key<Pic> picKey = ds.save(pic);

        cpk.keys = singletonList(picKey);

        //test with Key<Pic>
        final UpdateResults res = ds.update(ds.find(ContainsPicKey.class).filter("name", cpk.name),
                                            ds.createUpdateOperations(ContainsPicKey.class).set("keys", cpk.keys),
                                            new UpdateOptions());

        assertThat(res.getUpdatedCount(), is(1));

        //test reading the object.
        final ContainsPicKey cpk2 = ds.find(ContainsPicKey.class).get();
        assertThat(cpk2, is(notNullValue()));
        assertThat(cpk.name, is(cpk2.name));
        assertThat(cpk2.keys, hasItem(picKey));
    }

    @Test
    public void testUpdateRef() {
        final ContainsPic cp = new ContainsPic();
        cp.setName("cp one");

        getDatastore().save(cp);

        final Pic pic = new Pic();
        pic.setName("fist");
        final Key<Pic> picKey = getDatastore().save(pic);


        //test with Key<Pic>

        assertThat(getDatastore().update(getDatastore().find(ContainsPic.class).filter("name", cp.getName()),
                                       getDatastore().createUpdateOperations(ContainsPic.class)
                                                     .set("pic", pic),
                                       new UpdateOptions())
                                 .getUpdatedCount(),
                   is(1));

        //test reading the object.
        final ContainsPic cp2 = getDatastore().find(ContainsPic.class).get();
        assertThat(cp2, is(notNullValue()));
        assertThat(cp.getName(), is(cp2.getName()));
        assertThat(cp2.getPic(), is(notNullValue()));
        assertThat(cp2.getPic().getName(), is(notNullValue()));
        assertThat(pic.getName(), is(cp2.getPic().getName()));

        getDatastore().update(getDatastore().find(ContainsPic.class).filter("name", cp.getName()),
                       getDatastore().createUpdateOperations(ContainsPic.class)
                                     .set("pic", picKey),
                       new UpdateOptions());

        //test reading the object.
        final ContainsPic cp3 = getDatastore().find(ContainsPic.class).get();
        assertThat(cp3, is(notNullValue()));
        assertThat(cp.getName(), is(cp3.getName()));
        assertThat(cp3.getPic(), is(notNullValue()));
        assertThat(cp3.getPic().getName(), is(notNullValue()));
        assertThat(pic.getName(), is(cp3.getPic().getName()));
    }

    @Test
    public void testUpdateWithDifferentType() {
        final ContainsInt cInt = new ContainsInt();
        cInt.val = 21;
        getDatastore().save(cInt);

        final UpdateResults res = getDatastore().update(getDatastore().find(ContainsInt.class),
                                                 getDatastore().createUpdateOperations(ContainsInt.class).inc("val", 1.1D),
                                                 new UpdateOptions());
        assertUpdated(res, 1);

        assertThat(getDatastore().find(ContainsInt.class).get().val, is(22));
    }

    @Test(expected = ValidationException.class)
    public void testValidationBadFieldName() {
        getDatastore().update(getDatastore().find(Circle.class).field("radius").equal(0),
                       getDatastore().createUpdateOperations(Circle.class).inc("r", 1D));
    }

    @Test
    public void isolated() {
        UpdateOperations<Circle> updates = getDatastore().createUpdateOperations(Circle.class)
                                                         .inc("radius", 1D);
        assertFalse(updates.isIsolated());
        updates.isolated();
        assertTrue(updates.isIsolated());

        getDatastore().update(getDatastore().find(Circle.class)
                                            .field("radius").equal(0),
                       updates,
                       new UpdateOptions()
                           .upsert(true)
                           .writeConcern(WriteConcern.ACKNOWLEDGED));
    }

    private void assertInserted(final UpdateResults res) {
        assertThat(res.getInsertedCount(), is(1));
        assertThat(res.getUpdatedCount(), is(0));
        assertThat(res.getUpdatedExisting(), is(false));
    }

    private void assertUpdated(final UpdateResults res, final int count) {
        assertThat(res.getInsertedCount(), is(0));
        assertThat(res.getUpdatedCount(), is(count));
        assertThat(res.getUpdatedExisting(), is(true));
    }

    private EntityLogs createEntryLogs(final String key, final String value) {
        EntityLogs logs = new EntityLogs();
        logs.raw = new BasicDBObject(key, value);
        getDatastore().save(logs);

        return logs;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void validateNoClassName(final EntityLogs loaded) {
        List<DBObject> logs = (List<DBObject>) loaded.raw.get("logs");
        for (DBObject o : logs) {
            Assert.assertNull(o.get("className"));
        }
    }

    private static class ContainsIntArray {
        private final Integer[] values = {1, 2, 3};
        @Id
        private ObjectId id;
    }

    private static class ContainsInt {
        @Id
        private ObjectId id;
        private int val;
    }

    @Entity
    private static class ContainsPicKey {
        @Id
        private ObjectId id;
        private String name = "test";
        private Key<Pic> pic;
        private List<Key<Pic>> keys;
    }

    @Entity(noClassnameStored = true)
    public static class EntityLogs {
        @Id
        private ObjectId id;
        @Indexed
        private String uuid;
        @Embedded
        private List<EntityLog> logs = new ArrayList<EntityLog>();
        private DBObject raw;

        @PreLoad
        public void preload(final DBObject raw) {
            this.raw = raw;
        }
    }

    @Embedded
    public static class EntityLog {
        private Date receivedTs;
        private String value;

        public EntityLog() {
        }

        EntityLog(final String value, final Date date) {
            this.value = value;
            receivedTs = date;
        }

        @Override
        public int hashCode() {
            int result = receivedTs != null ? receivedTs.hashCode() : 0;
            result = 31 * result + (value != null ? value.hashCode() : 0);
            return result;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof EntityLog)) {
                return false;
            }

            final EntityLog entityLog = (EntityLog) o;

            return receivedTs != null ? receivedTs.equals(entityLog.receivedTs)
                                      : entityLog.receivedTs == null && (value != null ? value.equals(entityLog.value)
                                                                                       : entityLog.value == null);

        }


        @Override
        public String toString() {
            return String.format("EntityLog{receivedTs=%s, value='%s'}", receivedTs, value);
        }
    }

    private static final class Parent {
        @Embedded
        private final Set<Child> children = new HashSet<Child>();
        @Id
        private ObjectId id;
    }

    private static final class Child {
        private String first;
        private String last;

        private Child(final String first, final String last) {
            this.first = first;
            this.last = last;
        }

        private Child() {
        }

        @Override
        public int hashCode() {
            int result = first != null ? first.hashCode() : 0;
            result = 31 * result + (last != null ? last.hashCode() : 0);
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

            Child child = (Child) o;

            return first != null ? first.equals(child.first)
                                 : child.first == null && (last != null ? last.equals(child.last) : child.last == null);

        }
    }

    private static final class DumbColl {
        private String opaqueId;
        private List<DumbArrayElement> fromArray;

        private DumbColl() {
        }

        private DumbColl(final String opaqueId) {
            this.opaqueId = opaqueId;
        }
    }

    private static final class DumbArrayElement {
        private String whereId;

        private DumbArrayElement(final String whereId) {
            this.whereId = whereId;
        }
    }
}
