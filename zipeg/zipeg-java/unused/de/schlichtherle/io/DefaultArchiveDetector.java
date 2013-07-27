/*
 * DefaultArchiveDetector.java
 *
 * Created on 24. Dezember 2005, 00:01
 */
/*
 * Copyright 2005-2006 Schlichtherle IT Services
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.schlichtherle.io;

import de.schlichtherle.io.archive.spi.ArchiveDriver;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An {@link ArchiveDetector} which matches the names of files against the
 * suffix pattern given to its constructors in order to detect prospective
 * archive files and look up its registry to locate the corresponding
 * {@link ArchiveDriver}.
 * <p>
 * Where a constructor expects a <code>suffixes</code> parameter, this must
 * consist of the form <code>"suffix[|suffix]*"</code> where
 * <code>suffix</code> is a combination of case insensitive letters without
 * a leading dot.
 * Zero length or duplicated suffixes are silently ignored.
 * If this parameter is <code>null</code>, no archives are recognized.
 * As an example, the parameter <code>"zip|jar"</code> would cause the
 * archive detector to recognize ZIP and JAR files only.
 * <p>
 * For information on how to configure the plug-in archive driver registry,
 * please refer to the default configuration file
 * <code>META-INF/services/de.schlichtherle.io.archive.spi.ArchiveDriver.properties</code>
 * in the <code>truezip.jar</code>.
 * <p>
 * {@link ArchiveDriver} classes are loaded on demand by the
 * {@link #getArchiveDriver} method using the current thread's context class
 * loader. This usually happens when a client application instantiates the
 * {@link File} class.
 * <p>
 * This implementation is (virtually) immutable and thread safe.
 * 
 * @author Christian Schlichtherle
 * @version @version@
 * @since TrueZIP 6.0
 */
public class DefaultArchiveDetector extends AbstractArchiveDetector {

    //
    // Static fields.
    //

    private static final String CLASS_NAME
            = "de/schlichtherle/io/DefaultArchiveDetector".replace('/', '.'); // support code obfuscation!
    private static final ResourceBundle resources
            = ResourceBundle.getBundle(CLASS_NAME);
    private static final Logger logger
            = Logger.getLogger(CLASS_NAME, CLASS_NAME);
    
    private static final String SERVICE =
            "META-INF/services/de.schlichtherle.io.archive.spi.ArchiveDriver.properties";

    private static final Object INVALID_DRIVER = new Object();

    /**
     * The global map of all archive drivers configured in the set of all
     * configuration files.
     * Maps single suffixes (not sets) [<code>String</code>] to the <em>class
     * name</em> of an {@link ArchiveDriver} [<code>String</code>].
     */
    private static final Map allDrivers;

    /**
     * All archive types registered with this class as a suffix set.
     * <p>
     * This value is determined by processing all configuration files on the
     * class path which are provided by the plug-in archive drivers and
     * (optionally) the client application.
     * For more information, please refer to the default configuration file
     * <code>META-INF/services/de.schlichtherle.io.archive.spi.ArchiveDriver.properties</code>
     * on the class path of the TrueZIP JAR.
     * <p>
     * A suffix set is of the form "suffix[|suffix]*",
     * where each suffix does not contain the leading dot.
     * It's a set because it never contains duplicate suffixes.
     * E.g. "zip", "zip|jar", "tar" or "tgz|tar.gz" could be produced by the
     * configuration files.
     *
     * @deprecated This field is not for public use and will vanish in the
     *             next major release.
     *             Use <code>ArchiveDetector.ALL.getSuffixes()</code> instead.
     */
    public static final String ALL_SUFFIXES; // init by static initializer!

    /**
     * The archive types recognized by default as a suffix set.
     * <p>
     * This value is determined by processing all configuration files on the
     * class path which are provided by the plug-in archive drivers and
     * (optionally) the client application.
     * For more information, please refer to the default configuration file
     * <code>META-INF/services/de.schlichtherle.io.archive.spi.ArchiveDriver.properties</code>
     * on the class path of the TrueZIP JAR.
     * <p>
     * A suffix set is of the form "suffix[|suffix]*",
     * where each suffix does not contain the leading dot.
     * It's a set because it never contains duplicate suffixes.
     * E.g. "zip", "zip|jar", "tar" or "tgz|tar.gz" could be produced by the
     * configuration files.
     *
     * @deprecated This field is not for public use and will vanish in the
     *             next major release.
     *             Use <code>ArchiveDetector.DEFAULT.getSuffixes()</code> instead.
     */
    public static final String DEFAULT_SUFFIXES; // init by static initializer!

    //
    // Instance fields.
    //

    /**
     * The delegate used to lookup archive drivers when no driver is
     * configured locally.
     */
    private final DefaultArchiveDetector delegate;

    /**
     * The map of configured or cached drivers, mapping from single suffix
     * {@link String strings} (not sets) to {@link ArchiveDriver}s.
     * This may be shared by multiple archive detectors and threads,
     * so all access must be synchronized on the map instance
     * (<em>not</em> <code>this</code> instance)!
     */
    private final Map drivers;

    /**
     * The set of suffixes recognized by this archive detector.
     */
    private final String suffixes;

    /**
     * The thread local matcher used to match archive file name suffixes.
     */
    private final ThreadLocalMatcher matcher;

    //
    // Constructors.
    //

    /**
     * Creates a new instance of <code>DefaultArchiveDetector</code>.
     * This constructor recognizes any file which's path name matches the
     * given suffix list as an archive and uses the respective
     * {@link ArchiveDriver} as determined by the configuration files.
     * 
     * @param suffixes A list of suffixes which shall identify prospective
     *        archive files. May be <code>null</code> or empty.
     * @throws IllegalArgumentException If any of the suffixes in the suffix
     *         list names a suffix for which no {@link ArchiveDriver} is
     *         configured in the configuration files.
     * @see DefaultArchiveDetector Syntax explanation for <code>suffix</code>
     *      parameter.
     */
    public DefaultArchiveDetector(final String suffixes) {
        final SuffixSet suffixSet = new SuffixSet(suffixes);
        final Set allSuffixes = allDrivers.keySet();
        if (suffixSet.retainAll(allSuffixes)) {
            final SuffixSet unknown = new SuffixSet(suffixes);
            unknown.removeAll(allSuffixes);
            throw new IllegalArgumentException(unknown + " (no archive driver installed for these suffixes)");
        }

        assert configurationOK(allDrivers);

        this.delegate = this;
        this.drivers = allDrivers;
        this.suffixes = suffixSet.toString();
        this.matcher = new ThreadLocalMatcher(suffixSet.toRegex());
    }

    /**
     * Equivalent to
     * {@link #DefaultArchiveDetector(DefaultArchiveDetector, String, ArchiveDriver)
     * DefaultArchiveDetector(ArchiveDetector.NULL, suffixes, driver)}.
     */
    public DefaultArchiveDetector(String suffixes, ArchiveDriver driver) {
        this(NULL, suffixes, driver);
    }

    /**
     * Creates a new instance of <code>DefaultArchiveDetector</code> by
     * inheriting its settings from the given <code>template</code> and
     * adding a mapping for the given suffix list and driver to it.
     *
     * @param delegate The <code>DefaultArchiveDetector</code> which's
     *        configuration is to be inherited.
     * @param suffixes A non-empty suffix list, following the usual syntax.
     * @param driver The archive driver to register for the suffix list.
     *        This must either be an archive driver instance, a string
     *        with the class name of an archive driver implementation or a
     *        class instance of an archive driver implementation.
     * @throws IllegalArgumentException If any parameter is <code>null</code>.
     * @see DefaultArchiveDetector Syntax explanation for <code>suffix</code>
     *      parameter.
     */
    public DefaultArchiveDetector(
            DefaultArchiveDetector delegate,
            String suffixes,
            ArchiveDriver driver) {
        this(delegate, new Object[] { suffixes, driver});
    }

    /**
     * Creates a new instance of <code>DefaultArchiveDetector</code> by
     * inheriting its settings from the given <code>template</code> and
     * amending it by the mappings in <code>configuration</code>.
     * On any exception, processing is continued with the remaining entries in
     * the configuration and finally the last exception catched is (re)thrown.
     *
     * @param delegate The <code>DefaultArchiveDetector</code> which's
     *        configuration is to be inherited.
     * @param configuration The array of suffix lists and archive driver IDs.
     *        Each key in this map must be a non-empty suffix list,
     *        following the usual syntax.
     *        Each value must either be an archive driver instance, a class
     *        instance of an archive driver implementation, or a string
     *        with the class name of an archive driver implementation.
     * @throws NullPointerException If any parameter or configuration element
     *         is <code>null</code>.
     * @throws IllegalArgumentException If any other preconditions on the
     *         <code>configuration</code> does not hold.
     * @see DefaultArchiveDetector Syntax explanation for <code>suffix</code>
     *      parameter.
     */
    public DefaultArchiveDetector(
            final DefaultArchiveDetector delegate,
            Object[] configuration) {
        this(delegate, toMap(configuration));
    }

    /**
     * Creates a new instance of <code>DefaultArchiveDetector</code> by
     * inheriting its settings from the given <code>template</code> and
     * amending it by the mappings in <code>configuration</code>.
     * 
     * @param delegate The <code>DefaultArchiveDetector</code> which's
     *        configuration is to be inherited.
     * @param configuration The map of suffix lists and archive driver IDs.
     *        Each key in this map must be a non-empty suffix list,
     *        following the usual syntax.
     *        Each value must either be an archive driver instance, a class
     *        instance of an archive driver implementation, or a string
     *        with the class name of an archive driver implementation.
     * @throws NullPointerException If any parameter or configuration element
     *         is <code>null</code>.
     * @throws IllegalArgumentException If any other preconditions on the
     *         <code>configuration</code> does not hold.
     * @see DefaultArchiveDetector Syntax explanation for <code>suffix</code>
     *      parameter.
     */
    public DefaultArchiveDetector(
            final DefaultArchiveDetector delegate,
            final Map configuration) {
        if (delegate == null)
            throw new NullPointerException("delegate");
        checkConfiguration(configuration);

        this.delegate = delegate;
        drivers = new HashMap();
        registerArchiveDrivers(configuration, drivers);
        final SuffixSet suffixSet = new SuffixSet(delegate.suffixes); // may be a subset of delegate.drivers.keySet()!
        suffixSet.addAll(drivers.keySet());
        suffixes = suffixSet.toString();
        matcher = new ThreadLocalMatcher(suffixSet.toRegex());
    }

    //
    // Methods.
    //

    private static Map toMap(Object[] configuration) {
        if (configuration == null)
            return null;

        final Map map = new HashMap((int) (configuration.length / (2 * .75)));
        for (int i = 0, l = configuration.length; i < l; i += 2)
            map.put(configuration[i], configuration[i + 1]);

        return map;
    }

    private static boolean configurationOK(Map configuration) {
        try {
            checkConfiguration(configuration);
            return true;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private static void checkConfiguration(final Map configuration) {
        if (configuration == null)
            throw new NullPointerException("configuration");

        for (Iterator it = configuration.entrySet().iterator(); it.hasNext();) {
            final Map.Entry entry = (Map.Entry) it.next();
            final Object key = entry.getKey();
            if (!(key instanceof String))
                if (key != null)
                    throw new IllegalArgumentException("configuration key is not a string!");
                else
                    throw new NullPointerException("configuration key");
            final String suffixes = (String) key;
            if (suffixes.length() <= 0)
                throw new IllegalArgumentException("configuration key is empty!");
            if ("DRIVER".equals(suffixes))
                throw new IllegalArgumentException("DRIVER directive not allowed in configuration key!");
            if ("DEFAULT".equals(suffixes))
                throw new IllegalArgumentException("DEFAULT directive not allowed in configuration key!");

            final Object value = entry.getValue();
            if (value == null)
                throw new NullPointerException("configuration value");
            if (value instanceof ArchiveDriver)
                continue;
            if (value instanceof String) {
                if (((String) value).length() <= 0)
                    throw new IllegalArgumentException("configuration string value is empty!");
                continue;
            }
            if (value instanceof Class) {
                if (!ArchiveDriver.class.isAssignableFrom((Class) value))
                    throw new IllegalArgumentException("configuration class value is not an archive driver!");
                continue;
            }
            throw new IllegalArgumentException("configuration value is not an archive driver, class or string!");
        }
    }

    /**
     * Implements the {@link ArchiveDetector#getArchiveDriver} method.
     * {@link ArchiveDriver} classes are loaded on demand by this method
     * using the current thread's context class loader.
     * <p>
     * An archive driver is looked up as follows:
     * <ul>
     * <li>
     * If the configuration holds an instance of an <code>ArchiveDriver</code>
     * implementation, it is returned.
     * <li>
     * Otherwise, if the configuration holds a string, it is supposed to be
     * the fully qualified class name of an <code>ArchiveDriver</code>
     * implementation. The class will be loaded using the context class
     * loader of the current thread.
     * <li>
     * If the configuration holds a class instance, it will be instantiated
     * with its no-arguments constructor.
     * </ul>
     *
     * @throws NullPointerException If <code>pathname</code> is <code>null</code>.
     */
    public ArchiveDriver getArchiveDriver(final String pathname) {
        final Matcher m = matcher.reset(pathname);
        if (!m.matches())
            return null;
        return lookupArchiveDriver(m.group(1).toLowerCase());
    }

    private ArchiveDriver lookupArchiveDriver(final String suffix) {
        assert matcher.reset("." + suffix).matches();

        synchronized (drivers) {
            // Lookup driver locally.
            Object driver = drivers.get(suffix);
            if (driver instanceof ArchiveDriver) {
                return (ArchiveDriver) driver;
            } else if (driver == INVALID_DRIVER) {
                return null;
            } else if (driver == null) {
                // Lookup driver in delegate and cache.
                // Note that the delegate cannot not be null since otherwise
                // the matcher wouldn't have matched.
                driver = delegate.lookupArchiveDriver(suffix);
                drivers.put(suffix, driver != null ? driver : INVALID_DRIVER);
                return (ArchiveDriver) driver;
            } else {
                // We have found an entry in the drivers map, but it isn't
                // an ArchiveDriver, so we probably need to load its class first
                // and instantiate it.
                // Note that we will install drivers in this detector's local
                // drivers map in order to avoid multithreading issues when accessing
                // the global drivers map.
                try {
                    if (driver instanceof String)
                        driver = Thread.currentThread().getContextClassLoader()
                                .loadClass((String) driver);

                    assert driver instanceof Class
                            : "The constructor failed to ensure that all values in the drivers map are either ArchiveDriver, Class or String instances!";

                    driver = (ArchiveDriver) ((Class) driver).newInstance();
                    drivers.put(suffix, driver);
                    logger.log(Level.FINE, "driverInstalled",
                            new Object[] { suffix, driver });
                    return (ArchiveDriver) driver;
                } catch (Exception failure) {
                    // Map INVALID_DRIVER in order to prevent repeated loading or
                    // instantiation of this driver!
                    drivers.put(suffix, INVALID_DRIVER);
                    final String message = MessageFormat.format(
                            resources.getString("driverInstallationFailed"),
                            new Object[] { suffix, driver });
                    logger.log(Level.WARNING, message, failure);
                    return null;
                }
            }
        } // synchronized (drivers)
    }

    /**
     * Returns the set of suffixes identifying the archive types recognized
     * by this instance.
     *
     * @return suffixes A pattern of file name suffixes which shall identify
     *        prospective archive files detected by this instance.
     *        The pattern consists of the form
     *        <code>"suffix[|suffix]*"</code> where <code>suffix</code> is a
     *        combination of lower case letters without the leading dot.
     *        It's actually a set because it never contains duplicate suffixes.
     *        It also never contains empty suffixes.
     *        If <code>null</code>, this ArchiveDetector does not recognize
     *        any archives.
     *
     * @see #DefaultArchiveDetector(String)
     */
    public String getSuffixes() {
        return suffixes;
    }

    static {
        logger.config("banner");
        
        // Process configuration files on the class path of the current
        // thread's context class loader.
        allDrivers = registerArchiveDrivers();

        // Init suffixes.
        // Not that retrieval of the default suffix must be done first in
        // order to remove the DEFAULT key from the drivers map if present.
        // The driver installation would throw an exception
        // on this entry otherwise.
        DEFAULT_SUFFIXES = defaultSuffixes(allDrivers);
        ALL_SUFFIXES = new SuffixSet(allDrivers.keySet()).toString();

        // Log registered drivers.
        final Iterator it = allDrivers.entrySet().iterator();
        if (it.hasNext()) {
            do {
                final Map.Entry entry = (Map.Entry) it.next();
                logger.log(Level.CONFIG, "driverRegistered",
                        new Object[] { entry.getKey(), entry.getValue() });
            } while (it.hasNext());

            logger.log(Level.CONFIG, "allSuffixes", ALL_SUFFIXES);
            logger.log(Level.CONFIG, "defaultSuffixes", DEFAULT_SUFFIXES);
        } else {
            logger.warning("noDriversRegistered");
        }
    }

    /**
     * Iterates through all resource URLs found for <code>SERVICE</code>
     * and calls {@link #registerArchiveDrivers(URL, Map, Map)
     * registerArchiveDrivers(url, driverDrivers, clientDrivers)}
     * for each of them.
     */
    private static Map registerArchiveDrivers() {
        final Map driverDrivers = new HashMap();
        final Map clientDrivers = new HashMap();

        final Enumeration urls;
        try {
            urls = Thread.currentThread().getContextClassLoader()
                    .getResources(SERVICE);
        } catch (IOException failure) {
            logger.log(Level.WARNING, "resourceLookupFailed", SERVICE);
            return driverDrivers;
        }

        while (urls.hasMoreElements()) {
            final URL url = (URL) urls.nextElement();
            registerArchiveDrivers(
                    url, driverDrivers, clientDrivers);
        }

        // Ensure that client specified drivers always override
        // driver specified drivers.
        driverDrivers.putAll(clientDrivers);
        return driverDrivers;
    }

    /**
     * Loads and processes the given <code>url</code> in order to register
     * the archive drivers in its configuration file.
     */
    private static void registerArchiveDrivers(
            final URL url,
            final Map driverDrivers,
            final Map clientDrivers) {
        assert url != null;
        assert driverDrivers != null;
        assert clientDrivers != null;

        logger.log(Level.CONFIG, "loadingConfiguration", url);
        // Load the configuration map from the properties file.
        final Properties configuration = new Properties();
        try {
            final InputStream in = url.openStream();
            try {
                configuration.load(in);
                registerArchiveDrivers(
                        configuration, driverDrivers, clientDrivers);
            } finally {
                in.close();
            }
        } catch (IOException failure) {
            logger.log(Level.WARNING, "loadingConfigurationFailed", failure);
            // Continue normally.
        }
    }

    /**
     * Processes the given <code>configuration</code> in order to register
     * its archive drivers.
     */
    private static void registerArchiveDrivers(
            final Map configuration,
            final Map driverDrivers,
            final Map clientDrivers) {
        assert configuration != null;
        assert driverDrivers != null;
        assert clientDrivers != null;

        // Consume and process DRIVER entry.
        final String driver = (String) configuration.remove("DRIVER");
        final boolean isDriver = Boolean.TRUE.equals(Boolean.valueOf(driver));

        // Select registry.
        final Map drivers;
        if (isDriver)
            drivers = driverDrivers;
        else
            drivers = clientDrivers;

        registerArchiveDrivers(configuration, drivers);
    }

    /**
     * Processes the given map <code>configuration</code> with archive driver
     * mappings.
     * On any exception, processing is continued with the remaining entries in
     * the configuration and finally the last exception catched is (re)thrown.
     * 
     * 
     * @throws NullPointerException If any archive driver ID in the
     *         configuration is <code>null</code>.
     */
    private static void registerArchiveDrivers(
            final Map configuration,
            final Map drivers) {
        assert configuration != null;
        assert drivers != null;

        for (final Iterator i = configuration.entrySet().iterator(); i.hasNext(); ) {
            final Map.Entry entry = (Map.Entry) i.next();
            final String key = (String) entry.getKey();
            final Object id = entry.getValue();
            assert !"DRIVER".equals(key) : "DRIVER should have been removed from this map before this method was called!";
            if ("DEFAULT".equals(key)) {
                // Process manually - registerArchiveDriver would put the
                // lowercase representation of the key into the map and
                // register the keyword in the set of all suffixes!
                drivers.put(key, id);
            } else {
                registerArchiveDriver(key, id, drivers);
            }
        }
    }

    /**
     * Registers the given archive <code>id</code> for the given
     * <code>suffixes</code>.
     * 
     * @throws NullPointerException If <code>id</code> is <code>null</code>.
     */
    private static void registerArchiveDriver(
            final String suffixes,
            final Object id,
            final Map drivers) {
        assert drivers != null;

        if (id == null)
            throw new NullPointerException("Archive driver ID must not be null!");

        final SuffixSet suffixSet = new SuffixSet(suffixes);
        for (final Iterator it = suffixSet.iterator(); it.hasNext();) {
            final String suffix = (String) it.next();
            drivers.put(suffix, id);
        }
    }

    private static String defaultSuffixes(final Map drivers) {
        assert drivers != null;

        final String suffixes = (String) drivers.remove("DEFAULT");
        if (suffixes == null)
            return null;

        if ("NULL".equals(suffixes)) {
            return null;
        } else if ("ALL".equals(suffixes)) {
            return new SuffixSet(drivers.keySet()).toString();
        } else {
            final Set suffixSet = new SuffixSet(suffixes);
            for (final Iterator it = suffixSet.iterator(); it.hasNext();) {
                final String suffix = (String) it.next();
                if (!drivers.containsKey(suffix)) {
                    it.remove();
                    logger.log(Level.WARNING, "unknownSuffix", suffix);
                }
            }
            return suffixSet.toString();
        }
    }

    //
    // Member classes.
    //

    /**
     * An ordered set of normalized suffixes.
     */
    private static class SuffixSet extends TreeSet {
        /**
         * Constructs a new suffix set from the given suffix list.
         */
        public SuffixSet(final String suffixes) {
            if (suffixes == null)
                return;

            final String[] split = suffixes.split("\\|");
            for (int i = 0, l = split.length; i < l; i++) {
                final String suffix = split[i];
                if (suffix.length() > 0)
                    add(suffix);
            }
        }

        public SuffixSet(final Collection c) {
            super(c);
        }

        /**
         * @deprecated Use {@link #add(String)} instead!
         */
        public boolean add(Object o) {
            return add((String) o);
        }

        /**
         * Adds the normalized lowercase representation of suffix.
         *
         * @returns <code>true</code> iff a normalized suffix was added.
         * @throws NullPointerException If <code>suffix</code> is <code>null</code>.
         */
        public boolean add(String suffix) {
            // Normalize suffixes before adding them to the set.
            suffix = suffix.replaceAll("\\\\\\.", "\\.");
            if (suffix.length() > 0 && suffix.charAt(0) == '.')
                suffix = suffix.substring(1);
            if (suffix.length() > 0)
                return super.add(suffix.toLowerCase());
            return false;
        }

        public String toRegex() {
            if (isEmpty())
                return "\\00"; // NOT "\00"! Effectively never matches anything.

            final StringBuffer sb = new StringBuffer(".*\\.(");
            int c = 0;
            for (Iterator i = iterator(); i.hasNext(); ) {
                final String suffix = (String) i.next();
                if (c > 0)
                    sb.append('|');
                sb.append("\\Q");
                sb.append(suffix);
                sb.append("\\E");
                c++;
            }
            sb.append(')');

            return sb.toString();
        }
        
        public String toString() {
            if (isEmpty())
                return null;

            final StringBuffer sb = new StringBuffer();
            int c = 0;
            for (Iterator i = iterator(); i.hasNext(); ) {
                final String suffix = (String) i.next();
                if (c > 0)
                    sb.append('|');
                sb.append(suffix);
                c++;
            }
            assert c > 0;

            return sb.toString();
        }
    }

    private static class ThreadLocalMatcher {
        private final ThreadLocal tl;

        public ThreadLocalMatcher(final String regex) {
            tl = new ThreadLocal() {
                protected final Object initialValue() {
                    return Pattern.compile(
                            regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                            .matcher("");
                }
            };
        }

        public final Matcher reset(CharSequence input) {
            return ((Matcher) tl.get()).reset(input);
        }

        /*public final boolean matches() {
            return ((Matcher) tl.get()).matches();
        }

        public final String group(int i) {
            return ((Matcher) tl.get()).group(i);
        }*/
    }
}
