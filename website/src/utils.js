export const capitalize = str => {
  let splitStr = str.split(' ');
  return splitStr.map(word => {
    const fisrtChar  = word[0].toUpperCase()
    const rest = word.slice(1)
    return fisrtChar + rest
  }).reduce((acc, cur) => acc + " " + cur)
}