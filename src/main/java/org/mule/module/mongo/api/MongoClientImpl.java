/**
 * Mule MongoDB Cloud Connector
 *
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.module.mongo.api;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBObject;
import com.mongodb.MongoException;
import com.mongodb.MapReduceCommand.OutputType;
import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSDBFile;
import com.mongodb.gridfs.GridFSInputFile;

import java.io.InputStream;
import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.validation.constraints.NotNull;

import org.apache.commons.lang.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MongoClientImpl implements MongoClient
{
    private final DB db;
    private static Logger logger = LoggerFactory.getLogger(MongoClientImpl.class);

    public MongoClientImpl(DB db)
    {
        Validate.notNull(db);
        this.db = db;
    }

    public long countObjects(@NotNull String collection, DBObject query)
    {
        Validate.notNull(collection);
        if (query == null)
        {
            return openSession().getCollection(collection).count();
        }
        return openSession().getCollection(collection).count(query);
    }

    public void createCollection(@NotNull String collection, boolean capped, Integer maxObjects, Integer size)
    {
        Validate.notNull(collection);
        BasicDBObject options = new BasicDBObject("capped", capped);
        if (maxObjects != null)
        {
            options.put("maxObject", maxObjects);
        }
        if (size != null)
        {
            options.put("size", size);
        }
        openSession().createCollection(collection, options);
    }

    public void dropCollection(@NotNull String collection)
    {
        Validate.notNull(collection);
        openSession().getCollection(collection).drop();
    }

    public boolean existsCollection(@NotNull String collection)
    {
        Validate.notNull(collection);
        return openSession().collectionExists(collection);
    }

    public Iterable<DBObject> findObjects(@NotNull String collection, DBObject query, List<String> fields)
    {
        Validate.notNull(collection);
        return bug5588Workaournd(openSession().getCollection(collection).find(query, FieldsSet.from(fields)));
    }

    public DBObject findOneObject(@NotNull String collection, DBObject query, List<String> fields)
    {
        Validate.notNull(collection);
        DBObject element = openSession().getCollection(collection).findOne(query, FieldsSet.from(fields));
        if (element == null)
        {
            throw new MongoException("No object found for query " + query);
        }
        return element;
    }

    public void insertObject(@NotNull String collection,
                             @NotNull DBObject object,
                             @NotNull WriteConcern writeConcern)
    {
        Validate.notNull(collection);
        Validate.notNull(object);
        Validate.notNull(writeConcern);
        openSession().getCollection(collection).insert(object,
            writeConcern.toMongoWriteConcern(openSession()));
    }

    public Collection<String> listCollections()
    {
        return openSession().getCollectionNames();
    }

    public Iterable<DBObject> mapReduceObjects(@NotNull String collection,
                                               @NotNull String mapFunction,
                                               @NotNull String reduceFunction,
                                               String outputCollection)
    {
        Validate.notNull(collection);
        Validate.notEmpty(mapFunction);
        Validate.notEmpty(reduceFunction);
        return bug5588Workaournd(openSession().getCollection(collection).mapReduce(mapFunction,
            reduceFunction, outputCollection, outputTypeFor(outputCollection), null).results());
    }

    private OutputType outputTypeFor(String outputCollection)
    {
        return outputCollection != null ? OutputType.REPLACE : OutputType.INLINE;
    }

    public void removeObjects(@NotNull String collection, DBObject query, @NotNull WriteConcern writeConcern)
    {
        Validate.notNull(collection);
        Validate.notNull(writeConcern);
        openSession().getCollection(collection).remove(query != null ? query : new BasicDBObject(),
            writeConcern.toMongoWriteConcern(openSession()));
    }

    public void saveObject(@NotNull String collection,
                           @NotNull DBObject object,
                           @NotNull WriteConcern writeConcern)
    {
        Validate.notNull(collection);
        Validate.notNull(object);
        Validate.notNull(writeConcern);
        openSession().getCollection(collection).save(object, writeConcern.toMongoWriteConcern(openSession()));
    }

    public void updateObjects(@NotNull String collection,
                              DBObject query,
                              DBObject object,
                              boolean upsert,
                              boolean multi,
                              WriteConcern writeConcern)
    {
        Validate.notNull(collection);
        Validate.notNull(writeConcern);
        openSession().getCollection(collection).update(query, object, upsert, multi,
            writeConcern.toMongoWriteConcern(openSession()));

    }

    public void createIndex(String collection, String field, IndexOrder order)
    {
        openSession().getCollection(collection).createIndex(new BasicDBObject(field, order.getValue()));
    }

    public void dropIndex(String collection, String name)
    {
        openSession().getCollection(collection).dropIndex(name);
    }

    public Collection<DBObject> listIndices(String collection)
    {
        return openSession().getCollection(collection).getIndexInfo();
    }
    
    public DBObject createFile(InputStream content, String filename, String contentType, DBObject metadata)
    {
        Validate.notNull(filename);
        Validate.notNull(content);
        GridFSInputFile file = getGridFs().createFile(content);
        file.setFilename(filename);
        file.setContentType(contentType);
        if (metadata != null)
        {
            file.setMetaData(metadata);
        }
        file.save();
        return file;
    }

    public Iterable<DBObject> findFiles(DBObject query)
    {
        return bug5588Workaournd(getGridFs().find(query));
    }

    public DBObject findOneFile(DBObject query)
    {
        Validate.notNull(query);
        GridFSDBFile file = getGridFs().findOne(query);
        if (file == null)
        {
            throw new MongoException("No file found for query " + query);
        }
        return file;
    }

    public InputStream getFileContent(DBObject query)
    {
        Validate.notNull(query);
        return ((GridFSDBFile) findOneFile(query)).getInputStream();
    }
    
    public Iterable<DBObject> listFiles(DBObject query)
    {
        return bug5588Workaournd(getGridFs().getFileList(query));
    }
    
    public void removeFiles(DBObject query)
    {
        getGridFs().remove(query);
    }   
    
    private GridFS getGridFs()
    {
        return new GridFS(openSession());
    }
    /*
     * see http://www.mulesoft.org/jira/browse/MULE-5588
     */
    @SuppressWarnings("unchecked")
    private Iterable<DBObject> bug5588Workaournd(final Iterable<? extends DBObject> o)
    {
        if (o instanceof Collection<?>)
        {
            return (Iterable<DBObject>) o;
        }
        return new AbstractCollection<DBObject>()
        {

            @Override
            public Iterator<DBObject> iterator()
            {
                return (Iterator<DBObject>) o.iterator();
            }

            @Override
            public Object[] toArray()
            {
                warnEagerMessage("toArray");
                LinkedList<Object> l = new LinkedList<Object>();
                for (Object o : this)
                {
                    l.add(o);
                }
                return l.toArray();
            }

            @Override
            public int size()
            {
                warnEagerMessage("size");
                int i = 0;
                for (@SuppressWarnings("unused")
                Object o : this)
                {
                    i++;
                }
                return i;
            }

            /**
             * Same impl that those found in Object, in order to avoid eager elements
             * consumption
             */
            @Override
            public String toString()
            {
                return getClass().getName() + "@" + Integer.toHexString(hashCode());
            }

            /**
             * Warns that sending the given message implied processing all the
             * elements, which is not efficient at all, and most times is a bad idea,
             * as lazy iterables should be traversed only once and in a lazy manner.
             * 
             * @param message
             */
            private void warnEagerMessage(String message)
            {
                if (logger.isWarnEnabled())
                {
                    logger.warn(
                        "Method {} needs to consume all the element. It is inefficient and thus should be used with care",
                        message);
                }
            }
        };
    }

    /**
     * Gets the DB objects, ensuring that a consistent request is in progress.
     * Consistent requests are never ended, they end when the current thread finishes
     */
    private DB openSession()
    {
        db.requestStart();
        db.requestEnsureConnection();
        return db;
    }

    public DB getDb()
    {
        return db;
    }

}
