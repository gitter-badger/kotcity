package kotcity.ui.layers

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.event.EventHandler
import javafx.scene.canvas.GraphicsContext
import javafx.scene.image.Image
import javafx.scene.paint.Color
import javafx.util.Duration
import kotcity.data.BlockCoordinate
import kotcity.data.CityMap
import kotcity.data.Location
import kotcity.data.Zot
import kotcity.ui.ResizableCanvas
import kotcity.ui.map.CityRenderer
import kotcity.ui.sprites.ZotSpriteLoader
import kotcity.util.Debuggable
import kotcity.util.getRandomElement
import kotcity.util.getRandomElements
import java.util.concurrent.TimeUnit

class ZotRenderer(private val cityMap: CityMap, private val cityRenderer: CityRenderer, private val zotCanvas: ResizableCanvas): Debuggable {

    private var offsetTimeline: Timeline
    private var degree = 0.0

    init {

        this.offsetTimeline = Timeline(KeyFrame(Duration.millis(50.0), EventHandler {
            degree += 5
            if (degree >= 360) {
                degree = 0.0
            }
        }))

        offsetTimeline.cycleCount = Timeline.INDEFINITE
        offsetTimeline.play()
    }

    fun stop() {
        // stop animation thread here...
        offsetTimeline.stop()
    }

    private fun drawZot(image: Image, g2d: GraphicsContext, coordinate: BlockCoordinate) {
        val tx = coordinate.x - cityRenderer.blockOffsetX
        val ty = coordinate.y - cityRenderer.blockOffsetY
        val blockSize = cityRenderer.blockSize()
        // gotta fill that background too...

        val y = (ty - 1) * blockSize + ((Math.sin(Math.toRadians(degree)) * (blockSize * 0.1)))
        drawOutline(g2d, tx, blockSize, y)

        g2d.drawImage(image, tx * blockSize, y)
    }

    private fun drawOutline(g2d: GraphicsContext, tx: Double, blockSize: Double, y: Double) {
        g2d.fill = Color.WHITE

        val quarterBlock = blockSize * 0.25
        val halfBlock = blockSize * 0.5

        val x = (tx * blockSize) - quarterBlock
        val y = y - quarterBlock
        g2d.fillOval(x, y, blockSize + halfBlock, blockSize + halfBlock)

        g2d.stroke = Color.RED
        g2d.strokeOval(x, y, blockSize + halfBlock, blockSize + halfBlock)
    }

    fun render() {
        val gc = zotCanvas.graphicsContext2D
        gc.clearRect(0.0, 0.0, zotCanvas.width, zotCanvas.height)
        gc.fill = Color.AQUAMARINE

        // ok let's get all buildings with zots now...
        visibleBlockRange?.let { visibleBlockRange ->
            val locationsWithZots = visibleBuildingsCache[visibleBlockRange]
            locationsWithZots?.forEach { location ->
                // TODO: we gotta get different zots every once in a while...
                val randomZot: Zot? = randomZot(location)
                if (randomZot != null) {
                    val image = ZotSpriteLoader.spriteForZot(randomZot, cityRenderer.blockSize(), cityRenderer.blockSize())
                    if (image != null) {
                        drawZot(image, gc, location.coordinate)
                    }
                }
            }
        }

    }

    // OK we need a cache here so we only shoot back a few buildings
    private fun randomBuildingsWithZots(visibleBlockRange: Pair<BlockCoordinate, BlockCoordinate>): List<Location> {
        return cityMap.locationsIn(visibleBlockRange.first, visibleBlockRange.second).filter { it.building.zots.isNotEmpty() }
    }

    // this way we only flip around for a bit...
    private var visibleBuildingsCache: LoadingCache<Pair<BlockCoordinate, BlockCoordinate>, List<Location>> = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.SECONDS)
            .build<Pair<BlockCoordinate, BlockCoordinate>, List<Location>> { key -> randomBuildingsWithZots(key).getRandomElements(5) }


    private var zotForBuildingCache: LoadingCache<Location, Zot?> =  Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(15, TimeUnit.SECONDS)
            .build<Location, Zot?> { key -> key.building.zots.getRandomElement() }

    private fun randomZot(location: Location): Zot? {
        return zotForBuildingCache[location]
    }

    override var debug: Boolean = true
    var visibleBlockRange: Pair<BlockCoordinate, BlockCoordinate>? = null
}