package co.wethinkcode.logisticsconnect;

/**
 * A hub exactly as the export reports it, before any cleaning.
 * @param hubId         the source hub identifier, unpadded and unnormalized
 * @param province      the province the hub sits in, verbatim
 * @param sortingCenter the sorting center the hub feeds, verbatim
 * @param active        whether the hub is taking work, in whatever spelling the
 *                      export used
 */
public record Hub(String hubId, String province, String sortingCenter, String active) {
}
