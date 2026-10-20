package com.pradeep.spamblocker.domain

/**
 * Maps the free-text location that libphonenumber's offline geocoder returns for an Indian number
 * (a state, telecom circle or city) onto a state / UT name from [STATES].
 *
 * Mobile numbers: the series is allocated to a circle, but number portability means the caller may
 * no longer be there. Treat this as a good guess, not a guarantee.
 */
object IndiaRegions {

    val STATES: List<String> = listOf(
        "Andaman and Nicobar Islands", "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar",
        "Chandigarh", "Chhattisgarh", "Dadra and Nagar Haveli and Daman and Diu", "Delhi", "Goa",
        "Gujarat", "Haryana", "Himachal Pradesh", "Jammu and Kashmir", "Jharkhand", "Karnataka",
        "Kerala", "Ladakh", "Lakshadweep", "Madhya Pradesh", "Maharashtra", "Manipur", "Meghalaya",
        "Mizoram", "Nagaland", "Odisha", "Puducherry", "Punjab", "Rajasthan", "Sikkim", "Tamil Nadu",
        "Telangana", "Tripura", "Uttar Pradesh", "Uttarakhand", "West Bengal",
    )

    private val CITY_TO_STATE: Map<String, String> = mapOf(
        "mumbai" to "Maharashtra", "pune" to "Maharashtra", "nagpur" to "Maharashtra",
        "nashik" to "Maharashtra", "thane" to "Maharashtra", "aurangabad" to "Maharashtra",
        "ahmedabad" to "Gujarat", "surat" to "Gujarat", "vadodara" to "Gujarat", "baroda" to "Gujarat",
        "rajkot" to "Gujarat", "gandhinagar" to "Gujarat", "bhavnagar" to "Gujarat",
        "chennai" to "Tamil Nadu", "coimbatore" to "Tamil Nadu", "madurai" to "Tamil Nadu",
        "tiruchirappalli" to "Tamil Nadu", "trichy" to "Tamil Nadu", "salem" to "Tamil Nadu",
        "bengaluru" to "Karnataka", "bangalore" to "Karnataka", "mysore" to "Karnataka",
        "mysuru" to "Karnataka", "hubli" to "Karnataka", "mangalore" to "Karnataka",
        "hyderabad" to "Telangana", "secunderabad" to "Telangana", "warangal" to "Telangana",
        "visakhapatnam" to "Andhra Pradesh", "vijayawada" to "Andhra Pradesh", "tirupati" to "Andhra Pradesh",
        "kolkata" to "West Bengal", "calcutta" to "West Bengal", "howrah" to "West Bengal",
        "lucknow" to "Uttar Pradesh", "kanpur" to "Uttar Pradesh", "varanasi" to "Uttar Pradesh",
        "agra" to "Uttar Pradesh", "noida" to "Uttar Pradesh", "ghaziabad" to "Uttar Pradesh",
        "allahabad" to "Uttar Pradesh", "prayagraj" to "Uttar Pradesh", "meerut" to "Uttar Pradesh",
        "jaipur" to "Rajasthan", "jodhpur" to "Rajasthan", "udaipur" to "Rajasthan", "kota" to "Rajasthan",
        "bhopal" to "Madhya Pradesh", "indore" to "Madhya Pradesh", "gwalior" to "Madhya Pradesh",
        "jabalpur" to "Madhya Pradesh",
        "patna" to "Bihar", "ranchi" to "Jharkhand", "jamshedpur" to "Jharkhand",
        "bhubaneswar" to "Odisha", "cuttack" to "Odisha",
        "thiruvananthapuram" to "Kerala", "trivandrum" to "Kerala", "kochi" to "Kerala",
        "cochin" to "Kerala", "kozhikode" to "Kerala", "calicut" to "Kerala",
        "guwahati" to "Assam", "gurgaon" to "Haryana", "gurugram" to "Haryana", "faridabad" to "Haryana",
        "ludhiana" to "Punjab", "amritsar" to "Punjab", "jalandhar" to "Punjab",
        "dehradun" to "Uttarakhand", "shimla" to "Himachal Pradesh", "srinagar" to "Jammu and Kashmir",
        "jammu" to "Jammu and Kashmir", "raipur" to "Chhattisgarh", "panaji" to "Goa",
        "pondicherry" to "Puducherry",
    )

    private val STATES_BY_LENGTH = STATES.sortedByDescending { it.length }

    /** Returns every state / UT mentioned by [description]; empty if nothing recognisable. */
    fun toStates(description: String?): Set<String> {
        if (description.isNullOrBlank()) return emptySet()
        val lower = description.lowercase()
        val found = linkedSetOf<String>()
        var remaining = lower
        for (state in STATES_BY_LENGTH) {
            val s = state.lowercase()
            if (remaining.contains(s)) {
                found += state
                remaining = remaining.replace(s, " ")
            }
        }
        if (found.isEmpty()) {
            for ((city, state) in CITY_TO_STATE) {
                if (lower.contains(city)) found += state
            }
        }
        return found
    }
}
