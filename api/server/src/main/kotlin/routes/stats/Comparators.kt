package de.binarynoise.captiveportalautologin.server.routes.stats

import java.util.Comparator.comparingInt
import io.netty.util.NetUtil

object Comparators {
    object DomainComparator : Comparator<Any> {
        override fun compare(o1: Any, o2: Any): Int {
            o1 as String
            o2 as String
            if (o1 == o2) return 0
            
            fun isIP(domain: String): Boolean = NetUtil.isValidIpV4Address(domain) || NetUtil.isValidIpV6Address(domain)
            
            fun compareArray(a: List<String>, b: List<String>): Int {
                val minLength = minOf(a.size, b.size)
                for (i in 0 until minLength) {
                    val cmp = a[i].compareTo(b[i])
                    if (cmp != 0) return cmp
                }
                return a.size.compareTo(b.size)
            }
            
            fun <T> List<T>.dropOneButNotLast() = if (size <= 1) this else this.subList(1, size)
            
            val isIPA = isIP(o1)
            val isIPB = isIP(o2)
            val partsA = o1.split('.')
            val partsB = o2.split('.')
            
            return when {
                isIPA && isIPB -> compareArray(partsA, partsB)
                isIPA -> 1
                isIPB -> -1
                else -> {
                    // Compare domain components excluding TLD
                    val domainA = partsA.reversed().dropOneButNotLast()
                    val domainB = partsB.reversed().dropOneButNotLast()
                    val domainComparison = compareArray(domainA, domainB)
                    
                    if (domainComparison != 0) {
                        domainComparison
                    } else {
                        // If domains are equal, compare by TLD
                        val tldA = partsA.last()
                        val tldB = partsB.last()
                        tldA.compareTo(tldB)
                    }
                }
            }
        }
    }
    
    object VersionComparator : Comparator<Any> {
        val pattern = Regex("^(\\d+)([+-])([a-f0-9]+)(-\\d{8})(-dev)?$")
        
        override fun compare(o1: Any, o2: Any): Int {
            o1 as String
            o2 as String
            
            if (o1 == o2) return 0
            
            val match1 = pattern.matchEntire(o1)
            val match2 = pattern.matchEntire(o2)
            
            if (match1 == null || match2 == null) return o1.compareTo(o2)
            
            return comparingInt<MatchResult> { it.groupValues[1].toInt() }.thenComparing { it.groupValues[3] }
                .thenComparing { it.groupValues[2] }
                .thenComparing { it.groupValues[4] }
                .thenComparing { it.groupValues[5] }
                .compare(match1, match2)
        }
    }
    
    object RegularComparator : Comparator<Any> {
        override fun compare(o1: Any, o2: Any): Int {
            o1 as Comparable<Any>
//            o2 as Comparable<Any>
            return o1.compareTo(o2)
        }
    }
}
