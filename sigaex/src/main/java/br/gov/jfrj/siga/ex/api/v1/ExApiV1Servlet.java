package br.gov.jfrj.siga.ex.api.v1;

import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;

import com.crivano.swaggerservlet.SwaggerContext;
import com.crivano.swaggerservlet.SwaggerServlet;
import com.crivano.swaggerservlet.SwaggerUtils;
import com.crivano.swaggerservlet.dependency.TestableDependency;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.nimbusds.jwt.proc.JWTClaimsSetVerifier;

import br.gov.jfrj.siga.base.AplicacaoException;
import br.gov.jfrj.siga.base.Prop;
import br.gov.jfrj.siga.base.Prop.IPropertyProvider;
import br.gov.jfrj.siga.dp.DpPessoa;
import br.gov.jfrj.siga.dp.dao.DpPessoaDaoFiltro;
import br.gov.jfrj.siga.hibernate.ExDao;
import br.gov.jfrj.siga.idp.jwt.AuthJwtFormFilter;
import br.gov.jfrj.siga.model.ContextoPersistencia;

public class ExApiV1Servlet extends SwaggerServlet implements IPropertyProvider {
	private static final long serialVersionUID = 1756711359239182178L;

	public static ExecutorService executor = null;

	@Override
	public void initialize(ServletConfig config) throws ServletException {
		setAPI(IExApiV1.class);

		setActionPackage("br.gov.jfrj.siga.ex.api.v1");

		Prop.setProvider(this);
		Prop.defineGlobalProperties();
		defineProperties();

		// Threadpool
		if (Prop.get("redis.password") != null)
			SwaggerUtils.setCache(new MemCacheRedis());
		executor = Executors.newFixedThreadPool(Prop.getInt("threadpool.size"));

		class HttpGetDependency extends TestableDependency {
			String testsite;

			HttpGetDependency(String category, String service, String testsite, boolean partial, long msMin,
					long msMax) {
				super(category, service, partial, msMin, msMax);
				this.testsite = testsite;
			}

			@Override
			public String getUrl() {
				return testsite;
			}

			@Override
			public boolean test() throws Exception {
				final URL url = new URL(testsite);
				final URLConnection conn = url.openConnection();
				conn.connect();
				return true;
			}
		}

		class FileSystemWriteDependency extends TestableDependency {
			private static final String TESTING = "testing...";
			String path;

			FileSystemWriteDependency(String service, String path, boolean partial, long msMin, long msMax) {
				super("filesystem", service, partial, msMin, msMax);
				this.path = path;
			}

			@Override
			public String getUrl() {
				return path;
			}

			@Override
			public boolean test() throws Exception {
				Path file = Paths.get(path + "/test.temp");
				Files.write(file, TESTING.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE);
				String s = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
				return s != null;
			}
		}

		addDependency(
				new FileSystemWriteDependency("upload.dir.temp", Prop.get("upload.dir.temp"), false, 0, 10000));

		addDependency(new HttpGetDependency("rest", "www.google.com/recaptcha",
				"https://www.google.com/recaptcha/api/siteverify", false, 0, 10000));

		addDependency(new TestableDependency("database", "sigaexds", false, 0, 10000) {

			@Override
			public String getUrl() {
				return Prop.get("datasource.name");
			}

			@Override
			public boolean test() throws Exception {
				try {
					return ExDao.getInstance().dt() != null;
				} catch (Exception e) {
					e.printStackTrace(System.out);
					throw e;
				}
			}

			@Override
			public boolean isPartial() {
				return false;
			}
		});

		if (Prop.get("redis.password") != null)
			addDependency(new TestableDependency("cache", "redis", false, 0, 10000) {

				@Override
				public String getUrl() {
					return "redis://" + MemCacheRedis.getMasterHost() + ":" + MemCacheRedis.getMasterPort() + "/"
							+ MemCacheRedis.getDatabase() + " (" + "redis://" + MemCacheRedis.getSlaveHost() + ":"
							+ MemCacheRedis.getSlavePort() + "/" + MemCacheRedis.getDatabase() + ")";
				}

				@Override
				public boolean test() throws Exception {
					String uuid = UUID.randomUUID().toString();
					MemCacheRedis mc = new MemCacheRedis();
					mc.store("test", uuid.getBytes());
					String uuid2 = new String(mc.retrieve("test"));
					return uuid.equals(uuid2);
				}
			});

	}

	private void defineProperties() {
		addPublicProperty("carimbo.sistema", "siga");
		addPublicProperty("carimbo.url", null);
		addPublicProperty("carimbo.public.key", null);

		addPublicProperty("data.validar.assinatura.digital", "01/10/2020");
		addPublicProperty("data.validar.assinatura.com.senha", "01/10/2020");

		addRestrictedProperty("upload.dir.temp");

		addPrivateProperty("api.secret", null);

//		addPrivateProperty("jwt.secret", System.getProperty("idp.jwt.modulo.pwd.sigaidp"));
//		addPublicProperty("jwt.issuer", "siga");
//		addPublicProperty("cookie.name", "siga-jwt");
//		addPublicProperty("cookie.domain", null);
//		addPublicProperty("cookie.expire.seconds", Long.toString(20 * 60L)); // Expira em 20min
//		addPublicProperty("cookie.renew.seconds", Long.toString(15 * 60L)); // Renova 15min antes de expirar

		addRestrictedProperty("datasource.url", null);
		if (Prop.get("datasource.url") != null) {
			addRestrictedProperty("datasource.username");
			addPrivateProperty("datasource.password");
			addRestrictedProperty("datasource.name", null);
		} else {
			addRestrictedProperty("datasource.username", null);
			addPrivateProperty("datasource.password", null);
			addRestrictedProperty("datasource.name", "java:/jboss/datasources/SigaExDS");
		}

		// Redis
		//
		addRestrictedProperty("redis.database", "10");
		addPrivateProperty("redis.password", null);
		addRestrictedProperty("redis.slave.port", "0");
		addRestrictedProperty("redis.slave.host", null);
		addRestrictedProperty("redis.master.host", "localhost");
		addRestrictedProperty("redis.master.port", "6379");

		addPublicProperty("threadpool.size", "10");

		addPrivateProperty("assinador.externo.password", null);
		addPrivateProperty("assinador.externo.popup.url", "https://ittrufusion.appspot.com");
		addPublicProperty("assinatura.code.base.path", null);
		addPublicProperty("assinatura.messages.url.path", null);
		addPublicProperty("assinatura.policy.url.path", null);
		addRestrictedProperty("bie.lista.destinatario.publicacao", null);
		addPublicProperty("carimbo.texto.superior", "SIGA-DOC");
		addPublicProperty("classificacao.mascara.entrada",
				"([0-9]{0,2})\\.?([0-9]{0,2})?\\.?([0-9]{0,2})?\\.?([0-9]{0,2})?([A-Z])?");
		addPublicProperty("classificacao.mascara.exibicao", null);
		addPublicProperty("classificacao.mascara.javascript", "99.99.99.99");
		addPublicProperty("classificacao.mascara.nome.nivel", "NULL,Assunto,Classe,Subclasse,Atividade");
		addPublicProperty("classificacao.mascara.saida", "%1$02d.%2$02d.%3$02d.%4$02d");
		addPublicProperty("classificacao.nivel.minimo.de.enquadramento", null);
		addPublicProperty("codigo.acronimo.ano.inicial", "9999");
		addPublicProperty("conversor.html.ext", "br.gov.jfrj.itextpdf.MyPD4ML");
		addPublicProperty("conversor.html.factory", "br.gov.jfrj.siga.ex.ext.ConversorHTMLFactory");
		addPublicProperty("data.obrigacao.assinar.anexo.despacho", "31/12/2099");
		addPublicProperty("debug.modelo.padrao.arquivo", null);
		addPublicProperty("dje.lista.destinatario.publicacao", null);
		addPublicProperty("dje.servidor.data.disponivel", null);
		addPublicProperty("dje.servidor.url", null);
		addPublicProperty("email.mensagem.teste", null);
		addPublicProperty("folha.de.rosto", "inativa");
		addPublicProperty("modelo.interno.importado", null);
		addPublicProperty("modelo.processo.administrativo", null);
		addPublicProperty("montador.query", "br.gov.jfrj.siga.hibernate.ext.MontadorQuery");
		addPublicProperty("pdf.tamanho.maximo", "5000000");
		addPublicProperty("relarmaz.qtd.bytes.pagina", "51200");
		addPublicProperty("reordenacao.ativo", null);
		addPublicProperty("rodape.data.assinatura.ativa", "31/12/2099");
		addPrivateProperty("util.webservice.password", null);
		addPublicProperty("volume.max.paginas", "200");
		addPrivateProperty("webdav.senha", null);
		addPublicProperty("controlar.numeracao.expediente", "false");
		addPublicProperty("recebimento.automatico", "true");
		
		addPublicProperty("exibe.nome.acesso", "false");
				
		addPublicProperty("modelos.cabecalho.titulo", "JUSTIÇA FEDERAL");
		addPublicProperty("modelos.cabecalho.subtitulo", null);
		
		//Siga-Le
		addPublicProperty("smtp.sugestao.destinatario", getProp("/siga.smtp.usuario.remetente"));
		addPublicProperty("smtp.sugestao.assunto", "Siga-Le: Sugestão");
		
		addPublicProperty("api.security.authorization.type", "OAUTH2"); //BASIC_AUTH, OAUTH2
	}

	@Override
	public String getService() {
		return "sigaex";
	}

	@Override
	public String getUser() {
		return ContextoPersistencia.getUserPrincipal();
	}

	public static <T> Future<T> submitToExecutor(Callable<T> task) {
		return executor.submit(task);
	}

	@Override
	public void invoke(SwaggerContext context) throws Exception {
		try {
			
			if (context.getAction().getClass().isAnnotationPresent(AcessoPrivadoOAuth.class)) {
				try {
					//Extract Access Token
					String accessToken = AuthJwtFormFilter.extrairAuthorization(context.getRequest());	
					
					//Extract SIGA Profile for disambiguation CPF to SIGA Matricula 
					String sigaProfileBase64 = context.getRequest().getHeader("SIGAProfile");
					String sigaProfile = null;
					if (sigaProfileBase64 != null) {
						sigaProfile = new String(Base64.getDecoder().decode(sigaProfileBase64));
					}
					
	
					// Create a JWT processor for the access tokens
					ConfigurableJWTProcessor<SecurityContext> jwtProcessor =  new DefaultJWTProcessor<>();
					
					jwtProcessor.setJWSTypeVerifier( new DefaultJOSEObjectTypeVerifier<>(new JOSEObjectType("JWT")));
					
					//JWS
					JWKSource<SecurityContext> keySource =
						    new RemoteJWKSet<>(new URL("https://homolog.login.sp.gov.br/sts/.well-known/openid-configuration/jwks"));
					
					JWSAlgorithm expectedJWSAlg = JWSAlgorithm.RS256;
					JWSKeySelector<SecurityContext> keySelector = new JWSVerificationKeySelector<>(expectedJWSAlg, keySource);	
					jwtProcessor.setJWSKeySelector(keySelector);
					
					//Verify claims
					jwtProcessor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier(
						    new JWTClaimsSet.Builder().issuer("https://homolog.login.sp.gov.br/sts").build(),
						    new HashSet<>(Arrays.asList("exp","sub","client_id","scope"))));
	
					//Extract JWT claims
					JWTClaimsSet claimsSet = jwtProcessor.process(accessToken, null);
					Long cpf = Long.parseLong(claimsSet.getClaim("sub").toString());

					DpPessoa flt = new DpPessoa();
					List<DpPessoa> p = new ArrayList<DpPessoa>();
					if (sigaProfile != null) {
						flt.setSigla(sigaProfile);
						flt = ExDao.getInstance().consultarPorSigla(flt);
						if (flt != null)
							p.add(flt);
					} else {
						p =  ExDao.getInstance().listarPorCpf(cpf);
					}

					
					if (p.isEmpty()) {
						throw new AplicacaoException(
								"SIGAProfile não encontrado.");
					}
					
					if (p.size() > 1) {
						throw new AplicacaoException(
								"SIGAProfile não informado. Há mais de um SIGAProfile para esse escopo.");
					}
					
					if (cpf != p.get(0).getCpfPessoa().longValue()) {
						throw new AplicacaoException(
								"SIGAProfile não autorizado dentro desse escopo.");
					}
					ContextoPersistencia.setUserPrincipal(claimsSet.getClaim("sub").toString());
					System.out.println(claimsSet.toJSONObject());
					
				} catch (Exception e) {
					throw e;
				}		
				
			
			} else if (!context.getAction().getClass().isAnnotationPresent(AcessoPublico.class)) {
				try {
					String token = AuthJwtFormFilter.extrairAuthorization(context.getRequest());
					Map<String, Object> decodedToken = AuthJwtFormFilter.validarToken(token);
					final long now = System.currentTimeMillis() / 1000L;
					if ((Integer) decodedToken.get("exp") < now + AuthJwtFormFilter.TIME_TO_RENEW_IN_S) {
						// Seria bom incluir o attributo HttpOnly
						String tokenNew = AuthJwtFormFilter.renovarToken(token);
						@SuppressWarnings("unused")
						Map<String, Object> decodedNewToken = AuthJwtFormFilter.validarToken(token);
						Cookie cookie = AuthJwtFormFilter.buildCookie(tokenNew);
						context.getResponse().addCookie(cookie);
					}
					ContextoPersistencia.setUserPrincipal((String) decodedToken.get("sub"));
				} catch (Exception e) {
					if (!context.getAction().getClass().isAnnotationPresent(AcessoPublicoEPrivado.class))
						throw e;
				}
			}
			super.invoke(context);
		} finally {
			ContextoPersistencia.removeUserPrincipal();
		}
	}

	@Override
	public String getProp(String nome) {
		return getProperty(nome);
	}


}
