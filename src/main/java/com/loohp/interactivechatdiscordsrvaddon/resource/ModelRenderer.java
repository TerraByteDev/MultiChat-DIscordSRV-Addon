package com.loohp.interactivechatdiscordsrvaddon.resource;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.loohp.blockmodelrenderer.render.Hexahedron;
import com.loohp.blockmodelrenderer.render.Model;
import com.loohp.blockmodelrenderer.render.Point3D;
import com.loohp.interactivechatdiscordsrvaddon.Cache;
import com.loohp.interactivechatdiscordsrvaddon.InteractiveChatDiscordSrvAddon;
import com.loohp.interactivechatdiscordsrvaddon.graphics.ImageUtils;
import com.loohp.interactivechatdiscordsrvaddon.registies.ResourceRegistry;
import com.loohp.interactivechatdiscordsrvaddon.resource.models.BlockModel;
import com.loohp.interactivechatdiscordsrvaddon.resource.models.Coordinates3D;
import com.loohp.interactivechatdiscordsrvaddon.resource.models.ModelDisplay;
import com.loohp.interactivechatdiscordsrvaddon.resource.models.ModelDisplay.ModelDisplayPosition;
import com.loohp.interactivechatdiscordsrvaddon.resource.models.ModelElement;
import com.loohp.interactivechatdiscordsrvaddon.resource.models.ModelElement.ModelElementRotation;
import com.loohp.interactivechatdiscordsrvaddon.resource.models.ModelFace;
import com.loohp.interactivechatdiscordsrvaddon.resource.models.ModelFace.ModelFaceSide;
import com.loohp.interactivechatdiscordsrvaddon.resource.models.ModelManager;
import com.loohp.interactivechatdiscordsrvaddon.resource.models.ModelOverride.ModelOverrideType;
import com.loohp.interactivechatdiscordsrvaddon.resource.models.TextureUV;
import com.loohp.interactivechatdiscordsrvaddon.resource.texture.TextureAnimation;
import com.loohp.interactivechatdiscordsrvaddon.resource.texture.TextureMeta;
import com.loohp.interactivechatdiscordsrvaddon.resource.texture.TextureResource;

public class ModelRenderer implements AutoCloseable {
	
	public static final int INTERNAL_W = 64;
	public static final int INTERNAL_H = 64;
	
	public static final int QUALITY_THRESHOLD = 70;
	
	public static final int TEXTURE_RESOLUTION = 800;
	
	public static final String CACHE_KEY = "ModelRender";
	public static final String MODEL_NOT_FOUND = "notfound";
	
	private ExecutorService executor;
	private AtomicBoolean isValid;
	
	public ModelRenderer(int threads) {
		this.isValid = new AtomicBoolean(true);
		ThreadFactory factory = new ThreadFactoryBuilder().setNameFormat("InteractiveChatDiscordSRVAddon Async Model Processing Thread #%d").build();
		this.executor = new ThreadPoolExecutor(0, threads, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), factory);
	}
	
	@Override
	public synchronized void close() {
		isValid.set(false);
		executor.shutdown();
	}
	
	public RenderResult render(int width, int height, ResourceManager manager, String modelKey, ModelDisplayPosition displayPosition) {
		return render(width, height, manager, modelKey, displayPosition, Collections.emptyMap());
	}
	
	public RenderResult render(int width, int height, ResourceManager manager, String modelKey, ModelDisplayPosition displayPosition, Map<ModelOverrideType, Float> predicate) {
		return render(width, height, manager, modelKey, displayPosition, predicate, Collections.emptyMap());
	}
	
	public RenderResult render(int width, int height, ResourceManager manager, String modelKey, ModelDisplayPosition displayPosition, Map<ModelOverrideType, Float> predicate, Map<String, TextureResource> providedTextures) {
		String cacheKey = CACHE_KEY + "/" + modelKey + "/" + predicate.entrySet().stream().map(entry -> entry.getKey().name().toLowerCase() + ":" + entry.getValue().toString()).collect(Collectors.joining(";")) + "/" + providedTextures.entrySet().stream().map(entry -> entry.getKey() + ":" + (entry.getValue().isTexture() ? hash(entry.getValue().getTexture()) : "null")).collect(Collectors.joining(":"));
		Cache<?> cachedRender = Cache.getCache(cacheKey);
		if (cachedRender != null) {
			RenderResult cachedResult = (RenderResult) cachedRender.getObject();
			if (cachedResult.isSuccessful()) {
				return cachedResult;
			}
		}
		
		String rejectedReason = null;
		BlockModel blockModel = manager.getModelManager().resolveBlockModel(modelKey, predicate);
		if (blockModel == null) {
			return new RenderResult(MODEL_NOT_FOUND, null);
		}
		BufferedImage image = new BufferedImage(INTERNAL_W, INTERNAL_H, BufferedImage.TYPE_INT_ARGB);
		if (blockModel.getRawParent() == null || blockModel.getRawParent().indexOf("/") < 0) {
			render(blockModel, manager, image, displayPosition, providedTextures);
		} else if (blockModel.getRawParent().equals(ModelManager.ITEM_BASE)) {
			Graphics2D g = image.createGraphics();
			for (int i = 0; blockModel.getTextures().containsKey(ModelManager.ITEM_BASE_LAYER + i); i++) {
				TextureResource resource = manager.getTextureManager().getTexture(blockModel.getTextures().get(ModelManager.ITEM_BASE_LAYER + i));
				BufferedImage texture = resource.getTexture();
				if (resource.hasTextureMeta()) {
					TextureMeta meta = resource.getTextureMeta();
					if (meta.hasAnimation()) {
						TextureAnimation animation = meta.getAnimation();
						if (animation.hasWidth() && animation.hasHeight()) {
							texture = ImageUtils.copyAndGetSubImage(texture, 0, 0, animation.getWidth(), animation.getHeight());
						} else {
							texture = ImageUtils.copyAndGetSubImage(texture, 0, 0, texture.getWidth(), texture.getWidth());
						}
					}
				}
				g.drawImage(texture, 0, 0, image.getWidth(), image.getHeight(), null);
			}
			g.dispose();
		} else {
			rejectedReason = blockModel.getRawParent();
		}
		RenderResult result;
		if (rejectedReason == null) {
			result = new RenderResult(ImageUtils.resizeImageQuality(image, width, height), blockModel);
		} else {
			result = new RenderResult(rejectedReason == null ? "null" : rejectedReason, blockModel);
		}
		Cache.putCache(cacheKey, result, InteractiveChatDiscordSrvAddon.plugin.cacheTimeout);
		return result;
	}
	
	private void render(BlockModel blockModel, ResourceManager manager, BufferedImage image, ModelDisplayPosition displayPosition, Map<String, TextureResource> providedTextures) {
		Map<String, BufferedImage> cachedResize = new ConcurrentHashMap<>();
		List<ModelElement> elements = new ArrayList<>(blockModel.getElements());
		List<Hexahedron> hexahedrons = new ArrayList<>(elements.size());
		Queue<Future<Hexahedron>> tasks = new ConcurrentLinkedQueue<>();
		Iterator<ModelElement> itr = elements.iterator();
		while (itr.hasNext()) {
			ModelElement element = itr.next();
			tasks.add(executor.submit(() -> {
				ModelElementRotation rotation = element.getRotation();
				boolean ignoreZFight = false;
				if (rotation != null) {
					ignoreZFight = rotation.isRescale();
				}
				Hexahedron hexahedron = Hexahedron.fromCorners(new Point3D(element.getFrom().getX(), element.getFrom().getY(), element.getFrom().getZ()), new Point3D(element.getTo().getX(), element.getTo().getY(), element.getTo().getZ()), ignoreZFight, new BufferedImage[6]);
				BufferedImage[] images = new BufferedImage[6];
				int i = 0;
				for (ModelFaceSide side : ModelFaceSide.values()) {
					ModelFace faceData = element.getFace(side);
					if (faceData == null) {
						images[i] = null;
					} else {
						TextureUV uv = faceData.getUV();
						TextureResource resource = providedTextures.get(faceData.getTexture());
						if (resource == null) {
							resource = manager.getTextureManager().getTexture(faceData.getTexture(), false);
						}
						if (resource == null || !resource.isTexture()) {
							images[i] = null;
						} else if (uv != null && (uv.getXDiff() == 0 || uv.getYDiff() == 0)) {
							images[i] = null;
						} else {
							BufferedImage cached = cachedResize.get(faceData.getTexture());
							if (cached == null) {
								cached = resource.getTexture();
								if (resource.hasTextureMeta()) {
									TextureMeta meta = resource.getTextureMeta();
									if (meta.hasAnimation()) {
										TextureAnimation animation = meta.getAnimation();
										if (animation.hasWidth() && animation.hasHeight()) {
											cached = ImageUtils.copyAndGetSubImage(cached, 0, 0, animation.getWidth(), animation.getHeight());
										} else {
											cached = ImageUtils.copyAndGetSubImage(cached, 0, 0, cached.getWidth(), cached.getWidth());
										}
									}
								}
								if (cached.getWidth() > cached.getHeight()) {
									cached = ImageUtils.resizeImageFillWidth(cached, TEXTURE_RESOLUTION);
								} else {
									cached = ImageUtils.resizeImageFillHeight(cached, TEXTURE_RESOLUTION);
								}
								cachedResize.put(faceData.getTexture(), cached);
							}
							images[i] = ImageUtils.copyImage(cached);
							
							if (uv == null) {
								Point3D[] points;
								switch (side) {
								case DOWN:
									points = hexahedron.getDownFace().getPoints();
									break;
								case EAST:
									points = hexahedron.getEastFace().getPoints();
									break;
								case NORTH:
									points = hexahedron.getNorthFace().getPoints();
									break;
								case SOUTH:
									points = hexahedron.getSouthFace().getPoints();
									break;
								case UP:
									points = hexahedron.getUpFace().getPoints();
									break;
								case WEST:
								default:
									points = hexahedron.getWestFace().getPoints();
									break;
								}
								double x1;
								double y1;
								double x2;
								double y2;
								if (points[0].x == points[2].x) {
									x1 = points[0].y;
									y1 = points[0].z;
									x2 = points[2].y;
									y2 = points[2].z;
								} else if (points[0].y == points[2].y) {
									x1 = points[0].z;
									y1 = points[0].x;
									x2 = points[2].z;
									y2 = points[2].x;
								} else {
									x1 = points[0].y;
									y1 = points[0].x;
									x2 = points[2].y;
									y2 = points[2].x;
								}
								uv = new TextureUV(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2), Math.max(y1, y2));
							}
							uv = uv.getScaled(1, (double) images[i].getHeight() / (double) images[i].getWidth());
							uv = uv.getScaled((double) images[i].getWidth() / 16.0);
							double x1 = uv.getX1();
							double y1 = uv.getY1();
							double dX = Math.abs(uv.getXDiff());
							double dY = Math.abs(uv.getYDiff());
							if (uv.isVerticallyFlipped()) {
								images[i] = ImageUtils.flipVertically(images[i]);
								y1 = images[i].getHeight() - y1;
							}
							if (uv.isHorizontallyFlipped()) {
								images[i] = ImageUtils.flipHorizontal(images[i]);
								x1 = images[i].getWidth() - x1;
							}
							images[i] = ImageUtils.rotateImageByDegrees(ImageUtils.copyAndGetSubImage(images[i], (int) x1, (int) y1, Math.max(1, (int) dX), Math.max(1, (int) dY)), faceData.getRotation());
							if (faceData.getTintindex() == 0) {
								images[i] = ImageUtils.multiply(images[i], ResourceRegistry.TINT_INDEX_0_X, ResourceRegistry.TINT_INDEX_0_Y, ResourceRegistry.TINT_INDEX_0_Z);
							}
						}
					}
					i++;
				}
				hexahedron.setImage(images);
				if (rotation != null) {
					hexahedron.translate(-rotation.getOrigin().getX(), -rotation.getOrigin().getY(), -rotation.getOrigin().getZ());
					switch (rotation.getAxis()) {
					case X:
						hexahedron.rotate(rotation.getAngle(), 0, 0, false);
						break;
					case Y:
						hexahedron.rotate(0, rotation.getAngle(), 0, false);
						break;
					case Z:
					default:
						hexahedron.rotate(0, 0, rotation.getAngle(), false);
						break;
					}
					hexahedron.translate(rotation.getOrigin().getX(), rotation.getOrigin().getY(), rotation.getOrigin().getZ());
				}
				return hexahedron;
			}));
			itr.remove();
		}
		while (!tasks.isEmpty() || !elements.isEmpty()) {
			Future<Hexahedron> task = tasks.poll();
			if (task == null) {
				try {
					TimeUnit.MILLISECONDS.sleep(1);
				} catch (InterruptedException e) {}
			} else {
				try {
					hexahedrons.add(task.get());
				} catch (InterruptedException | ExecutionException e) {
					e.printStackTrace();
				}
			}
		}
		Model renderModel = new Model(hexahedrons);
		
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
		g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_SPEED);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g.translate(image.getWidth() / 2, image.getHeight() / 2);
		g.scale(image.getWidth() / 16, image.getHeight() / 16);
		renderModel.translate(-16 / 2, -16 / 2, -16 / 2);
		ModelDisplay displayData = blockModel.getDisplay(displayPosition);
		if (displayData != null) {
			Coordinates3D scale = displayData.getScale();
			renderModel.scale(scale.getX(), scale.getY(), scale.getZ());
			Coordinates3D rotation = displayData.getRotation();
			renderModel.rotate(rotation.getX(), rotation.getY(), rotation.getZ(), true);
			Coordinates3D transform = displayData.getTranslation();
			renderModel.translate(transform.getX(), transform.getY(), transform.getZ());
		}
		renderModel.updateLightingRatio(0.98, 0.98, 0.608, 0.8, 0.608, 0.8);
		renderModel.render(image.getWidth(), image.getHeight(), g, image, blockModel.getElements().size() <= QUALITY_THRESHOLD);
		g.dispose();
	}
	
	private String hash(BufferedImage image) {
		StringBuilder sb = new StringBuilder();
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				sb.append(Integer.toHexString(image.getRGB(x, y)));
			}
		}
		return sb.toString();
	}
	
	public static class RenderResult {
		
		private BufferedImage image;
		private String rejectedReason;
		private BlockModel model;
		
		public RenderResult(BufferedImage image, BlockModel model) {
			this.image = image;
			this.rejectedReason = null;
			this.model = model;
		}
		
		public RenderResult(String rejectedReason, BlockModel model) {
			this.image = null;
			this.rejectedReason = rejectedReason;
			this.model = model;
		}
		
		public boolean isSuccessful() {
			return image != null;
		}
		
		public BufferedImage getImage() {
			return ImageUtils.copyImage(image);
		}
		
		public String getRejectedReason() {
			return rejectedReason;
		}

		public BlockModel getModel() {
			return model;
		}
		
		public boolean hasModel() {
			return model != null;
		}
		
	}

}
