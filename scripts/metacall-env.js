/** Run from within the Metacall REPL to get these values */
function main() {
    console.log(`export LOADER_SCRIPT_PATH='${process.env.LOADER_SCRIPT_PATH}'`)
    console.log(`export LOADER_LIBRARY_PATH='${process.env.LOADER_LIBRARY_PATH}'`)
    console.log(`export LD_LIBRARY_PATH='${process.env.LOADER_LIBRARY_PATH}'`)
    console.log(`export CONFIGURATION_PATH='${process.env.CONFIGURATION_PATH}'`)
    console.log(`export SERIAL_LIBRARY_PATH='${process.env.SERIAL_LIBRARY_PATH}'`)
    console.log(`export DETOUR_LIBRARY_PATH='${process.env.DETOUR_LIBRARY_PATH}'`)
    console.log(`export PORT_LIBRARY_PATH='${process.env.PORT_LIBRARY_PATH}'`)
}

main()

module.exports = { main }