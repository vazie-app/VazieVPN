package app.vazie.vpn.core.model

/** The one configuration-shaped string Vazie is allowed to show a person, and the rules that make it safe to
 * show. */
object SyntheticConfigExample {

    /** The documentation host. RFC 2606 §3 reserves `example.com` precisely so that written material can name
     * a host without naming anybody's. */
    const val HOST: String = "vpn.example.com"

    /** A UUID that is the right shape and obviously nobody's. */
    const val USER_ID: String = "00000000-0000-0000-0000-000000000001"

    /** The VLESS link as a reader should meet it: enough to recognise, shortened where it stops teaching. */
    const val VLESS_LINK: String = "vless://$USER_ID@$HOST:443?..."
}
