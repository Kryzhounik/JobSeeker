/**
 * Shared contracts, temporarily hosted in Tools.
 *
 * <p>Driver/api currently implements the facade and depends on lower-level
 * modules. Making those modules depend on that API would create a dependency
 * cycle. Shared contracts stay in Tools until the project's own IoC and the
 * corresponding separation of module dependencies are introduced. Introducing
 * IoC or restructuring modules is not part of this change.
 */
package com.seeker.tools.api;
