package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2002-2012 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;

import org.apache.log4j.Logger;
import org.intermine.sql.Database;
import org.intermine.sql.DatabaseFactory;

/**
 * @author Julie Sullivan
 */
public class OntologyIdResolverFactory extends IdResolverFactory
{
    protected static final Logger LOG = Logger.getLogger(OntologyIdResolverFactory.class);
    private Database db;
    private String ontology = null;
    private static final String MOCK_TAXON_ID = "0";
    private final String propName = "db.production";


    /**
     * Construct with SO term of the feature type to read from chado database.
     *
     * @param ontology the feature type to resolve
     */
    public OntologyIdResolverFactory(String ontology) {
        this.ontology = ontology;
    }

    /**
     * Return an IdResolver, if not already built then create it.
     * @return a specific IdResolver
     */
    public IdResolver getIdResolver() {
        return getIdResolver(true);
    }

    /**
     * Return an IdResolver, if not already built then create it.  If failOnError
     * set to false then swallow any exceptions and return null.  Allows code to
     * continue if no resolver can be set up.
     * @param failOnError if false swallow any exceptions and return null
     * @return a specific IdResolver
     */
    public IdResolver getIdResolver(boolean failOnError) {
        if (!caughtError) {
            try {
                createIdResolver();
            } catch (Exception e) {
                this.caughtError = true;
                if (failOnError) {
                    throw new RuntimeException(e);
                }
            }
        }
        return resolver;
    }

    /**
     * Build an IdResolver.
     * @return an IdResolver for GO
     */
    @Override
    protected void createIdResolver() {

        if (resolver == null) {
            resolver = new IdResolver(clsName);
        }

        if (resolver.hasTaxon(MOCK_TAXON_ID)) {
            return;
        }

        try {
            // TODO we already know this database, right?
            db = DatabaseFactory.getDatabase(propName);

            String cacheFileName = "build/" + db.getName() + "." + ontology;
            File f = new File(cacheFileName);
            if (f.exists()) {
                System.out .println("OntologyIdResolver reading from cache file: " + cacheFileName);
                createFromFile(ontology, f);
            } else {
                System.out .println("OntologyIdResolver creating from database: " + db.getName());
                createFromDb(db);
                resolver.writeToFile(f);
                System.out .println("OntologyIdResolver caching in file: " + cacheFileName);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void createFromDb(Database database) {

        Connection conn = null;
        try {
            conn = database.getConnection();
            String query = "select t.identifier, s.name "
                + "from ontologytermsynonyms j, ontologytermsynonym s, ontologyterm t, ontology o "
                + "where t.ontologyid = o.id and o.name = 'GO' and t.id = j.ontologyterm "
                + "and j.synonyms = s.id and s.name LIKE 'GO:%'";

            LOG.info("QUERY: " + query);
            Statement stmt = conn.createStatement();
            ResultSet res = stmt.executeQuery(query);
            int i = 0;
            while (res.next()) {
                String uniquename = res.getString("identifier");
                String synonym = res.getString("name");
                resolver.addMainIds(MOCK_TAXON_ID, uniquename, Collections.singleton(synonym));
            }
            stmt.close();
            LOG.info("dbxref query returned " + i + " rows.");
        } catch (Exception e) {
            LOG.error(e);
            throw new RuntimeException(e);
        } finally {
            try {
                if (conn != null) {
                    conn.close();
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
