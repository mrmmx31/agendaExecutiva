# Compatibilidade da Mertto ZL02D

Data da descoberta: 03/09/2026.

## Perfil sem identificador pessoal

- produto comercial: Mertto ZL02D Sport;
- família informada pela tela `Sobre`: ZL02CPro;
- firmware informado: 2.0.9;
- classe: smartband Bluetooth dependente de aplicativo companheiro;
- sistema: não é Wear OS;
- interfaces de desenvolvimento disponíveis: nenhuma confirmada.

O trecho adicional exibido em `Sobre` pode identificar a unidade Bluetooth e
não deve entrar no Git, relatórios ou logs compartilhados.

## Consequência arquitetural

O APK `wear` e a Wear Data Layer não funcionam nesse dispositivo. A Agenda deve
emitir uma notificação Android normal no telefone; o aplicativo companheiro da
smartband decide se encaminha texto e qual efeito sensorial produz. Não existe
base para prometer que
ações Android sejam exibidas ou retornem eventos à Agenda.

| Capacidade | Resultado físico no Da Fit/ZL02CPRO 2.0.9 |
|---|---|
| texto do alerta | suportado por `Outras notificações` |
| alerta sonoro no pulso | suportado quando a notificação Android declara vibração, mesmo com `sound=null` |
| vibração no pulso | não ocorreu; o telefone vibrou, mas a pulseira somente tocou |
| `Concluir` no pulso | indisponível; somente texto foi espelhado |
| `Adiar` no pulso | indisponível; somente texto foi espelhado |
| operação offline própria | indisponível sem app executável no wearable |
| dados de saúde | fora do escopo sem API oficial e consentimento novo |

`Concluir` e `Adiar` continuam disponíveis no telefone. A experiência completa
no pulso permanece implementada no módulo Wear OS e validada em AVD; validação
física dessa experiência exige futuramente um relógio Wear OS ou SDK oficial
documentado para outro wearable.

O modo de compatibilidade para smartbands fechadas deve ser opt-in e explicar
que habilitar o alerta sensorial do companheiro também vibra o telefone. O efeito
final no pulso pertence ao Da Fit: neste ensaio ele produziu áudio e ignorou a
vibração configurada na pulseira. A aplicação não deve prometer seleção de áudio
ou vibração independente nesse hardware.

## Gate físico cauteloso

1. confirmar no telefone qual aplicativo companheiro está realmente pareado;
2. permitir nesse aplicativo somente notificações da variante `.fieldtest`;
3. usar títulos e horários fictícios e volume baixo;
4. testar conectada, Bluetooth desligado e reconexão;
5. registrar apenas `suportado`, `indisponível` ou `falhou` por capacidade;
6. não capturar BLE, extrair banco do companheiro ou testar dados pessoais;
7. revogar o acesso concedido ao terminar, se solicitado pelo proprietário.

Esta limitação é de hardware/plataforma, não falha do módulo Wear OS.

## Referências técnicas

- [manual do fabricante FCC da família ZL02CPro](https://fccid.io/2A5HP-ZL02CPRO/User-Manual/Users-Manual-7285481.pdf);
- [depuração Android por Wi-Fi](https://developer.android.com/studio/run/device).
