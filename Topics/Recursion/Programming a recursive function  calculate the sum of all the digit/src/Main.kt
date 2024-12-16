fun digitSum (n: Int): Int {
    if(n < 10)  return n
    val firstInt = n.toString().first().digitToInt()
    val rest = n.toString().substring(1).toInt()

    return firstInt + digitSum(rest)
}